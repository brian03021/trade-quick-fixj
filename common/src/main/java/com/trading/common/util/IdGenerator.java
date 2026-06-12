package com.trading.common.util;

import java.util.UUID;

/**
 * Utility class for generating unique identifiers
 */
public final class IdGenerator {
    
    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Generate a unique order ID
     */
    public static String generateOrderId() {
        return "ORD-" + UUID.randomUUID().toString();
    }

    /**
     * Generate a unique trade ID
     */
    public static String generateTradeId() {
        return "TRD-" + UUID.randomUUID().toString();
    }

    /**
     * Generate a unique event ID
     */
    public static String generateEventId() {
        return "EVT-" + UUID.randomUUID().toString();
    }

    /**
     * Generate a generic UUID
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}

// Made with Bob
