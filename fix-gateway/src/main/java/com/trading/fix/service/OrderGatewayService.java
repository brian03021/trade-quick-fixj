package com.trading.fix.service;

import com.trading.common.model.*;
import com.trading.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import quickfix.SessionID;
import quickfix.field.OrdType;
import quickfix.field.Side;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service to handle order gateway operations
 * Converts FIX messages to domain objects and publishes to Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGatewayService {

    private final KafkaTemplate<String, Order> orderKafkaTemplate;

    /**
     * Handle new order from FIX client
     */
    public void handleNewOrder(
            SessionID sessionId,
            String clOrdID,
            String symbol,
            char fixSide,
            char fixOrdType,
            double quantity,
            Double price,
            String account) {
        
        log.info("Handling new order: clOrdID={}, symbol={}, side={}, type={}, qty={}, price={}", 
                clOrdID, symbol, fixSide, fixOrdType, quantity, price);
        
        // Convert FIX fields to domain model
        com.trading.common.model.Side side = convertSide(fixSide);
        OrderType orderType = convertOrderType(fixOrdType);
        
        // Validate
        if (orderType == OrderType.LIMIT && price == null) {
            throw new IllegalArgumentException("Price required for limit orders");
        }
        
        // Create order
        String orderId = IdGenerator.generateOrderId();
        Order order = new Order(
            orderId,
            clOrdID,
            account,
            symbol,
            side,
            orderType,
            price != null ? BigDecimal.valueOf(price) : null,
            (long) quantity,
            0L,
            OrderStatus.NEW,
            Instant.now(),
            Instant.now()
        );
        
        // Publish to Kafka for order service to process
        orderKafkaTemplate.send("order.new", orderId, order);
        log.info("Published new order to Kafka: {}", orderId);
    }

    /**
     * Handle cancel order request from FIX client
     */
    public void handleCancelOrder(
            SessionID sessionId,
            String origClOrdID,
            String clOrdID,
            String symbol) {
        
        log.info("Handling cancel order: origClOrdID={}, clOrdID={}, symbol={}", 
                origClOrdID, clOrdID, symbol);
        
        // Publish cancel request to Kafka
        // In a real system, you'd look up the order by origClOrdID
        orderKafkaTemplate.send("order.cancel", origClOrdID, null);
        log.info("Published cancel request to Kafka: {}", origClOrdID);
    }

    /**
     * Convert FIX side to domain side
     */
    private com.trading.common.model.Side convertSide(char fixSide) {
        return switch (fixSide) {
            case Side.BUY -> com.trading.common.model.Side.BUY;
            case Side.SELL -> com.trading.common.model.Side.SELL;
            default -> throw new IllegalArgumentException("Invalid side: " + fixSide);
        };
    }

    /**
     * Convert FIX order type to domain order type
     */
    private OrderType convertOrderType(char fixOrdType) {
        return switch (fixOrdType) {
            case OrdType.MARKET -> OrderType.MARKET;
            case OrdType.LIMIT -> OrderType.LIMIT;
            case OrdType.STOP -> OrderType.STOP;
            case OrdType.STOP_LIMIT -> OrderType.STOP_LIMIT;
            default -> throw new IllegalArgumentException("Invalid order type: " + fixOrdType);
        };
    }
}

// Made with Bob
