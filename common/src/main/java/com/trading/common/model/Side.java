package com.trading.common.model;

/**
 * Order side enumeration
 */
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}

// Made with Bob
