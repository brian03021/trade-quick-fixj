package com.trading.matching.engine;

import com.trading.common.model.Order;
import com.trading.common.model.Side;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance order book with price-time priority
 * Uses ConcurrentSkipListMap for O(log n) operations
 */
@Slf4j
public class OrderBook {
    
    private final String symbol;
    
    // Bids: highest price first (descending)
    private final ConcurrentSkipListMap<BigDecimal, Queue<Order>> bids;
    
    // Asks: lowest price first (ascending)
    private final ConcurrentSkipListMap<BigDecimal, Queue<Order>> asks;
    
    // Order lookup by ID
    private final Map<String, Order> orderMap;
    
    // Lock for matching operations
    private final ReadWriteLock lock;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
        this.asks = new ConcurrentSkipListMap<>();
        this.orderMap = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Add order to book
     */
    public void addOrder(Order order) {
        lock.writeLock().lock();
        try {
            if (!order.getSymbol().equals(symbol)) {
                throw new IllegalArgumentException("Order symbol mismatch");
            }
            
            BigDecimal price = order.getPrice();
            if (price == null) {
                throw new IllegalArgumentException("Market orders not supported in order book");
            }
            
            ConcurrentSkipListMap<BigDecimal, Queue<Order>> book = 
                order.getSide() == Side.BUY ? bids : asks;
            
            book.computeIfAbsent(price, k -> new LinkedList<>()).add(order);
            orderMap.put(order.getOrderId(), order);
            
            log.debug("Added order to book: {} {} @ {} qty {}", 
                order.getSide(), symbol, price, order.getQuantity());
                
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove order from book
     */
    public boolean removeOrder(String orderId) {
        lock.writeLock().lock();
        try {
            Order order = orderMap.remove(orderId);
            if (order == null) {
                return false;
            }
            
            ConcurrentSkipListMap<BigDecimal, Queue<Order>> book = 
                order.getSide() == Side.BUY ? bids : asks;
            
            Queue<Order> priceLevel = book.get(order.getPrice());
            if (priceLevel != null) {
                priceLevel.remove(order);
                if (priceLevel.isEmpty()) {
                    book.remove(order.getPrice());
                }
            }
            
            log.debug("Removed order from book: {}", orderId);
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get best bid price
     */
    public Optional<BigDecimal> getBestBid() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(bids.firstKey());
        } catch (NoSuchElementException e) {
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get best ask price
     */
    public Optional<BigDecimal> getBestAsk() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(asks.firstKey());
        } catch (NoSuchElementException e) {
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get top of book (best bid and ask)
     */
    public Map<String, BigDecimal> getTopOfBook() {
        lock.readLock().lock();
        try {
            Map<String, BigDecimal> top = new HashMap<>();
            getBestBid().ifPresent(bid -> top.put("bid", bid));
            getBestAsk().ifPresent(ask -> top.put("ask", ask));
            return top;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get order by ID
     */
    public Optional<Order> getOrder(String orderId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(orderMap.get(orderId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all orders at a price level
     */
    public List<Order> getOrdersAtPrice(Side side, BigDecimal price) {
        lock.readLock().lock();
        try {
            ConcurrentSkipListMap<BigDecimal, Queue<Order>> book = 
                side == Side.BUY ? bids : asks;
            
            Queue<Order> priceLevel = book.get(price);
            return priceLevel != null ? new ArrayList<>(priceLevel) : Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get market depth (top N levels)
     */
    public Map<String, List<PriceLevel>> getDepth(int levels) {
        lock.readLock().lock();
        try {
            Map<String, List<PriceLevel>> depth = new HashMap<>();
            
            List<PriceLevel> bidLevels = new ArrayList<>();
            int count = 0;
            for (Map.Entry<BigDecimal, Queue<Order>> entry : bids.entrySet()) {
                if (count++ >= levels) break;
                long totalQty = entry.getValue().stream()
                    .mapToLong(Order::getRemainingQuantity)
                    .sum();
                bidLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
            }
            
            List<PriceLevel> askLevels = new ArrayList<>();
            count = 0;
            for (Map.Entry<BigDecimal, Queue<Order>> entry : asks.entrySet()) {
                if (count++ >= levels) break;
                long totalQty = entry.getValue().stream()
                    .mapToLong(Order::getRemainingQuantity)
                    .sum();
                askLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
            }
            
            depth.put("bids", bidLevels);
            depth.put("asks", askLevels);
            return depth;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get total order count
     */
    public int getOrderCount() {
        return orderMap.size();
    }

    /**
     * Clear all orders
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            bids.clear();
            asks.clear();
            orderMap.clear();
            log.info("Cleared order book for {}", symbol);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Price level representation
     */
    public record PriceLevel(BigDecimal price, long quantity, int orderCount) {}
}

// Made with Bob
