package com.trading.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.trading.common.model.Order;

import java.time.Instant;

/**
 * Base class for order-related events
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderAcceptedEvent.class, name = "ORDER_ACCEPTED"),
    @JsonSubTypes.Type(value = OrderRejectedEvent.class, name = "ORDER_REJECTED"),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class, name = "ORDER_CANCELLED"),
    @JsonSubTypes.Type(value = OrderFilledEvent.class, name = "ORDER_FILLED")
})
public abstract class OrderEvent {
    private final String eventId;
    private final String orderId;
    private final Instant timestamp;

    protected OrderEvent(String eventId, String orderId, Instant timestamp) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public String getOrderId() { return orderId; }
    public Instant getTimestamp() { return timestamp; }
}

/**
 * Event published when an order is accepted
 */
class OrderAcceptedEvent extends OrderEvent {
    private final Order order;

    @JsonCreator
    public OrderAcceptedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("order") Order order,
            @JsonProperty("timestamp") Instant timestamp) {
        super(eventId, orderId, timestamp);
        this.order = order;
    }

    public Order getOrder() { return order; }
}

/**
 * Event published when an order is rejected
 */
class OrderRejectedEvent extends OrderEvent {
    private final String reason;

    @JsonCreator
    public OrderRejectedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("reason") String reason,
            @JsonProperty("timestamp") Instant timestamp) {
        super(eventId, orderId, timestamp);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}

/**
 * Event published when an order is cancelled
 */
class OrderCancelledEvent extends OrderEvent {
    private final String reason;

    @JsonCreator
    public OrderCancelledEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("reason") String reason,
            @JsonProperty("timestamp") Instant timestamp) {
        super(eventId, orderId, timestamp);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}

/**
 * Event published when an order is filled
 */
class OrderFilledEvent extends OrderEvent {
    private final Order order;

    @JsonCreator
    public OrderFilledEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("order") Order order,
            @JsonProperty("timestamp") Instant timestamp) {
        super(eventId, orderId, timestamp);
        this.order = order;
    }

    public Order getOrder() { return order; }
}

// Made with Bob
