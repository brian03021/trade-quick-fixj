package com.trading.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Position domain model
 */
public final class Position {
    
    @NotBlank
    private final String accountId;
    
    @NotBlank
    private final String symbol;
    
    private final long quantity;
    
    @NotNull
    private final BigDecimal avgPrice;
    
    private final BigDecimal unrealizedPnL;
    
    @NotNull
    private final Instant updatedAt;

    @JsonCreator
    public Position(
            @JsonProperty("accountId") String accountId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("avgPrice") BigDecimal avgPrice,
            @JsonProperty("unrealizedPnL") BigDecimal unrealizedPnL,
            @JsonProperty("updatedAt") Instant updatedAt) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.unrealizedPnL = unrealizedPnL;
        this.updatedAt = updatedAt;
    }

    // Getters
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public long getQuantity() { return quantity; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public BigDecimal getUnrealizedPnL() { return unrealizedPnL; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isLong() {
        return quantity > 0;
    }

    public boolean isShort() {
        return quantity < 0;
    }

    public boolean isFlat() {
        return quantity == 0;
    }

    /**
     * Update position with a new trade
     */
    public Position updateWithTrade(Side side, long tradeQuantity, BigDecimal tradePrice) {
        long newQuantity = side == Side.BUY ? 
            quantity + tradeQuantity : 
            quantity - tradeQuantity;
        
        BigDecimal newAvgPrice;
        if (newQuantity == 0) {
            newAvgPrice = BigDecimal.ZERO;
        } else {
            // Calculate new average price
            BigDecimal currentValue = avgPrice.multiply(BigDecimal.valueOf(Math.abs(quantity)));
            BigDecimal tradeValue = tradePrice.multiply(BigDecimal.valueOf(tradeQuantity));
            
            if ((quantity > 0 && side == Side.BUY) || (quantity < 0 && side == Side.SELL)) {
                // Adding to position
                newAvgPrice = currentValue.add(tradeValue)
                    .divide(BigDecimal.valueOf(Math.abs(newQuantity)), BigDecimal.ROUND_HALF_UP);
            } else {
                // Reducing or reversing position
                newAvgPrice = Math.abs(newQuantity) > Math.abs(quantity) ? tradePrice : avgPrice;
            }
        }
        
        return new Position(accountId, symbol, newQuantity, newAvgPrice, 
                          unrealizedPnL, Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return Objects.equals(accountId, position.accountId) &&
               Objects.equals(symbol, position.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol);
    }

    @Override
    public String toString() {
        return "Position{" +
                "accountId='" + accountId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", avgPrice=" + avgPrice +
                ", unrealizedPnL=" + unrealizedPnL +
                '}';
    }
}

// Made with Bob
