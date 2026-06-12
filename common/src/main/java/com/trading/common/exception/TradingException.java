package com.trading.common.exception;

/**
 * Base exception for trading platform
 */
public class TradingException extends RuntimeException {
    
    public TradingException(String message) {
        super(message);
    }

    public TradingException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Exception thrown when order validation fails
 */
class OrderValidationException extends TradingException {
    public OrderValidationException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when risk check fails
 */
class RiskViolationException extends TradingException {
    public RiskViolationException(String message) {
        super(message);
    }
}

/**
 * Exception thrown when order is not found
 */
class OrderNotFoundException extends TradingException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}

/**
 * Exception thrown when duplicate order is detected
 */
class DuplicateOrderException extends TradingException {
    public DuplicateOrderException(String clientOrderId) {
        super("Duplicate order detected: " + clientOrderId);
    }
}

// Made with Bob
