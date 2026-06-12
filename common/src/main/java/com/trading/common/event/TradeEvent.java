package com.trading.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trading.common.model.Trade;

import java.time.Instant;

/**
 * Event published when a trade is executed
 */
public class TradeEvent {
    private final String eventId;
    private final Trade trade;
    private final Instant timestamp;

    @JsonCreator
    public TradeEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("trade") Trade trade,
            @JsonProperty("timestamp") Instant timestamp) {
        this.eventId = eventId;
        this.trade = trade;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public Trade getTrade() { return trade; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "TradeEvent{" +
                "eventId='" + eventId + '\'' +
                ", trade=" + trade +
                ", timestamp=" + timestamp +
                '}';
    }
}

// Made with Bob
