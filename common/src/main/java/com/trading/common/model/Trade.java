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
 * Immutable Trade domain model
 */
public final class Trade {
    
    @NotBlank
    private final String tradeId;
    
    @NotBlank
    private final String buyOrderId;
    
    @NotBlank
    private final String sellOrderId;
    
    @NotBlank
    private final String symbol;
    
    @NotNull
    @Positive
    private final BigDecimal price;
    
    @Positive
    private final long quantity;
    
    @NotNull
    private final Instant executedAt;

    @JsonCreator
    public Trade(
            @JsonProperty("tradeId") String tradeId,
            @JsonProperty("buyOrderId") String buyOrderId,
            @JsonProperty("sellOrderId") String sellOrderId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("executedAt") Instant executedAt) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
    }

    // Getters
    public String getTradeId() { return tradeId; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public Instant getExecutedAt() { return executedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return Objects.equals(tradeId, trade.tradeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId);
    }

    @Override
    public String toString() {
        return "Trade{" +
                "tradeId='" + tradeId + '\'' +
                ", buyOrderId='" + buyOrderId + '\'' +
                ", sellOrderId='" + sellOrderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", executedAt=" + executedAt +
                '}';
    }
}

// Made with Bob
