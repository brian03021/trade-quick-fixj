package com.trading.matching.engine;

import com.trading.common.event.TradeEvent;
import com.trading.common.model.Order;
import com.trading.common.model.Side;
import com.trading.common.model.Trade;
import com.trading.common.util.IdGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance matching engine
 * Implements price-time priority matching algorithm
 */
@Slf4j
@Component
public class MatchingEngine {

    private final Map<String, OrderBook> orderBooks;
    private final KafkaTemplate<String, TradeEvent> tradeKafkaTemplate;
    private final KafkaTemplate<String, Order> orderKafkaTemplate;
    
    // Metrics
    private final Counter matchCounter;
    private final Timer matchingLatency;

    public MatchingEngine(
            KafkaTemplate<String, TradeEvent> tradeKafkaTemplate,
            KafkaTemplate<String, Order> orderKafkaTemplate,
            MeterRegistry meterRegistry) {
        this.orderBooks = new ConcurrentHashMap<>();
        this.tradeKafkaTemplate = tradeKafkaTemplate;
        this.orderKafkaTemplate = orderKafkaTemplate;
        
        // Initialize metrics
        this.matchCounter = Counter.builder("matching.trades")
            .description("Number of trades matched")
            .register(meterRegistry);
        this.matchingLatency = Timer.builder("matching.latency")
            .description("Order matching latency")
            .register(meterRegistry);
    }

    /**
     * Submit order for matching
     */
    public void submitOrder(Order order) {
        Timer.Sample sample = Timer.start();
        
        try {
            log.info("Submitting order for matching: {}", order.getOrderId());
            
            OrderBook book = orderBooks.computeIfAbsent(
                order.getSymbol(), 
                OrderBook::new
            );
            
            // Try to match the order
            List<Trade> trades = matchOrder(book, order);
            
            // If order has remaining quantity, add to book
            if (order.getRemainingQuantity() > 0) {
                book.addOrder(order);
                publishOrderUpdate(order);
            }
            
            // Publish trades
            for (Trade trade : trades) {
                publishTrade(trade);
                matchCounter.increment();
            }
            
            sample.stop(matchingLatency);
            
        } catch (Exception e) {
            log.error("Error matching order: {}", order.getOrderId(), e);
            throw e;
        }
    }

    /**
     * Cancel order
     */
    public boolean cancelOrder(String symbol, String orderId) {
        log.info("Cancelling order: {} for symbol: {}", orderId, symbol);
        
        OrderBook book = orderBooks.get(symbol);
        if (book == null) {
            log.warn("Order book not found for symbol: {}", symbol);
            return false;
        }
        
        return book.removeOrder(orderId);
    }

    /**
     * Match order against order book
     * Returns list of trades executed
     */
    private List<Trade> matchOrder(OrderBook book, Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        
        // Determine which side of the book to match against
        Side matchSide = incomingOrder.getSide().opposite();
        
        while (incomingOrder.getRemainingQuantity() > 0) {
            // Get best price from opposite side
            BigDecimal bestPrice = matchSide == Side.BUY ? 
                book.getBestBid().orElse(null) : 
                book.getBestAsk().orElse(null);
            
            if (bestPrice == null) {
                // No orders to match against
                break;
            }
            
            // Check if prices cross
            if (!pricesCross(incomingOrder, bestPrice)) {
                break;
            }
            
            // Get orders at best price
            List<Order> ordersAtPrice = book.getOrdersAtPrice(matchSide, bestPrice);
            
            for (Order restingOrder : ordersAtPrice) {
                if (incomingOrder.getRemainingQuantity() == 0) {
                    break;
                }
                
                // Calculate trade quantity
                long tradeQty = Math.min(
                    incomingOrder.getRemainingQuantity(),
                    restingOrder.getRemainingQuantity()
                );
                
                // Create trade
                Trade trade = createTrade(incomingOrder, restingOrder, bestPrice, tradeQty);
                trades.add(trade);
                
                // Update orders
                Order updatedIncoming = incomingOrder.withFilledQuantity(
                    incomingOrder.getFilledQuantity() + tradeQty
                );
                Order updatedResting = restingOrder.withFilledQuantity(
                    restingOrder.getFilledQuantity() + tradeQty
                );
                
                // Remove filled resting order from book
                if (updatedResting.getRemainingQuantity() == 0) {
                    book.removeOrder(restingOrder.getOrderId());
                }
                
                // Update incoming order reference
                incomingOrder = updatedIncoming;
                
                // Publish order updates
                publishOrderUpdate(updatedResting);
                
                log.info("Matched {} units at {} for orders {} and {}", 
                    tradeQty, bestPrice, 
                    incomingOrder.getOrderId(), restingOrder.getOrderId());
            }
        }
        
        return trades;
    }

    /**
     * Check if prices cross (can match)
     */
    private boolean pricesCross(Order incomingOrder, BigDecimal restingPrice) {
        BigDecimal incomingPrice = incomingOrder.getPrice();
        
        if (incomingPrice == null) {
            // Market order always crosses
            return true;
        }
        
        if (incomingOrder.getSide() == Side.BUY) {
            // Buy order: incoming price >= resting ask price
            return incomingPrice.compareTo(restingPrice) >= 0;
        } else {
            // Sell order: incoming price <= resting bid price
            return incomingPrice.compareTo(restingPrice) <= 0;
        }
    }

    /**
     * Create trade from matched orders
     */
    private Trade createTrade(
            Order incomingOrder, 
            Order restingOrder, 
            BigDecimal price, 
            long quantity) {
        
        String tradeId = IdGenerator.generateTradeId();
        
        // Determine buy and sell order IDs
        String buyOrderId = incomingOrder.getSide() == Side.BUY ? 
            incomingOrder.getOrderId() : restingOrder.getOrderId();
        String sellOrderId = incomingOrder.getSide() == Side.SELL ? 
            incomingOrder.getOrderId() : restingOrder.getOrderId();
        
        return new Trade(
            tradeId,
            buyOrderId,
            sellOrderId,
            incomingOrder.getSymbol(),
            price,
            quantity,
            Instant.now()
        );
    }

    /**
     * Publish trade event to Kafka
     */
    private void publishTrade(Trade trade) {
        TradeEvent event = new TradeEvent(
            IdGenerator.generateEventId(),
            trade,
            Instant.now()
        );
        
        tradeKafkaTemplate.send("trade.executed", trade.getTradeId(), event);
        log.info("Published trade event: {}", trade.getTradeId());
    }

    /**
     * Publish order update to Kafka
     */
    private void publishOrderUpdate(Order order) {
        orderKafkaTemplate.send("order.updated", order.getOrderId(), order);
        log.debug("Published order update: {}", order.getOrderId());
    }

    /**
     * Get order book for symbol
     */
    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    /**
     * Get all symbols with active order books
     */
    public List<String> getActiveSymbols() {
        return new ArrayList<>(orderBooks.keySet());
    }

    /**
     * Get market depth for symbol
     */
    public Map<String, List<OrderBook.PriceLevel>> getDepth(String symbol, int levels) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) {
            return Map.of("bids", List.of(), "asks", List.of());
        }
        return book.getDepth(levels);
    }
}

// Made with Bob
