package com.trading.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Order domain model
 */
public final class Order {
    
    @NotBlank
    private final String orderId;
    
    @NotBlank
    private final String clientOrderId;
    
    @NotBlank
    private final String accountId;
    
    @NotBlank
    private final String symbol;
    
    @NotNull
    private final Side side;
    
    @NotNull
    private final OrderType type;
    
    private final BigDecimal price;
    
    @Positive
    private final long quantity;
    
    private final long filledQuantity;
    
    @NotNull
    private final OrderStatus status;
    
    @NotNull
    private final Instant createdAt;
    
    @NotNull
    private final Instant updatedAt;

    @JsonCreator
    public Order(
            @JsonProperty("orderId") String orderId,
            @JsonProperty("clientOrderId") String clientOrderId,
            @JsonProperty("accountId") String accountId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("side") Side side,
            @JsonProperty("type") OrderType type,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("filledQuantity") long filledQuantity,
            @JsonProperty("status") OrderStatus status,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt) {
        this.orderId = orderId;
        this.clientOrderId = clientOrderId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public String getOrderId() { return orderId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public OrderType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public long getFilledQuantity() { return filledQuantity; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Builder pattern for immutable updates
    public Order withStatus(OrderStatus newStatus) {
        return new Order(orderId, clientOrderId, accountId, symbol, side, type, 
                        price, quantity, filledQuantity, newStatus, createdAt, Instant.now());
    }

    public Order withFilledQuantity(long newFilledQuantity) {
        OrderStatus newStatus = newFilledQuantity >= quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        return new Order(orderId, clientOrderId, accountId, symbol, side, type, 
                        price, quantity, newFilledQuantity, newStatus, createdAt, Instant.now());
    }

    public long getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public boolean isActive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", clientOrderId='" + clientOrderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", type=" + type +
                ", price=" + price +
                ", quantity=" + quantity +
                ", filledQuantity=" + filledQuantity +
                ", status=" + status +
                '}';
    }
}

// Made with Bob
