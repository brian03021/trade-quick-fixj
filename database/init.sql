-- Trading Platform Database Schema
-- PostgreSQL 16+

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Orders table
CREATE TABLE orders (
    order_id VARCHAR(50) PRIMARY KEY,
    client_order_id VARCHAR(50) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(20) NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT')),
    price DECIMAL(18,6),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    filled_quantity BIGINT DEFAULT 0 CHECK (filled_quantity >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED', 'PENDING_CANCEL', 'EXPIRED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_client_order UNIQUE(client_order_id, account_id),
    CONSTRAINT valid_filled_quantity CHECK (filled_quantity <= quantity)
);

-- Indexes for orders
CREATE INDEX idx_orders_symbol_status ON orders(symbol, status) WHERE status IN ('NEW', 'PARTIALLY_FILLED');
CREATE INDEX idx_orders_account ON orders(account_id);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_client_order_id ON orders(client_order_id);

-- Trades table
CREATE TABLE trades (
    trade_id VARCHAR(50) PRIMARY KEY,
    buy_order_id VARCHAR(50) NOT NULL,
    sell_order_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    price DECIMAL(18,6) NOT NULL CHECK (price > 0),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(order_id)
);

-- Indexes for trades
CREATE INDEX idx_trades_symbol ON trades(symbol);
CREATE INDEX idx_trades_executed_at ON trades(executed_at DESC);
CREATE INDEX idx_trades_buy_order ON trades(buy_order_id);
CREATE INDEX idx_trades_sell_order ON trades(sell_order_id);

-- Positions table
CREATE TABLE positions (
    account_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0,
    avg_price DECIMAL(18,6) NOT NULL DEFAULT 0,
    unrealized_pnl DECIMAL(18,6) DEFAULT 0,
    realized_pnl DECIMAL(18,6) DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, symbol)
);

-- Indexes for positions
CREATE INDEX idx_positions_account ON positions(account_id);
CREATE INDEX idx_positions_symbol ON positions(symbol);

-- Risk limits table
CREATE TABLE risk_limits (
    account_id VARCHAR(50) PRIMARY KEY,
    max_order_size BIGINT NOT NULL CHECK (max_order_size > 0),
    max_position_size BIGINT NOT NULL CHECK (max_position_size > 0),
    max_daily_loss DECIMAL(18,6) NOT NULL CHECK (max_daily_loss > 0),
    max_daily_volume BIGINT NOT NULL CHECK (max_daily_volume > 0),
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Daily trading statistics
CREATE TABLE daily_stats (
    account_id VARCHAR(50) NOT NULL,
    trade_date DATE NOT NULL,
    total_volume BIGINT DEFAULT 0,
    total_trades INTEGER DEFAULT 0,
    realized_pnl DECIMAL(18,6) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, trade_date)
);

-- Audit log table
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    order_id VARCHAR(50),
    account_id VARCHAR(50),
    symbol VARCHAR(20),
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit log
CREATE INDEX idx_audit_log_order_id ON audit_log(order_id);
CREATE INDEX idx_audit_log_account_id ON audit_log(account_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_payload ON audit_log USING GIN (payload);

-- Market data table (for reference prices)
CREATE TABLE market_data (
    symbol VARCHAR(20) PRIMARY KEY,
    last_price DECIMAL(18,6),
    bid_price DECIMAL(18,6),
    ask_price DECIMAL(18,6),
    volume BIGINT DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_positions_updated_at BEFORE UPDATE ON positions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_limits_updated_at BEFORE UPDATE ON risk_limits
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_daily_stats_updated_at BEFORE UPDATE ON daily_stats
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert sample risk limits
INSERT INTO risk_limits (account_id, max_order_size, max_position_size, max_daily_loss, max_daily_volume)
VALUES 
    ('DEFAULT', 10000, 100000, 50000.00, 1000000),
    ('DEMO', 1000, 10000, 5000.00, 100000);

-- Insert sample market data
INSERT INTO market_data (symbol, last_price, bid_price, ask_price, volume)
VALUES 
    ('AAPL', 150.00, 149.95, 150.05, 0),
    ('GOOGL', 2800.00, 2799.50, 2800.50, 0),
    ('MSFT', 380.00, 379.90, 380.10, 0),
    ('TSLA', 250.00, 249.80, 250.20, 0);

-- Create view for active orders
CREATE VIEW active_orders AS
SELECT * FROM orders
WHERE status IN ('NEW', 'PARTIALLY_FILLED')
ORDER BY created_at;

-- Create view for daily trading summary
CREATE VIEW daily_trading_summary AS
SELECT 
    account_id,
    trade_date,
    total_volume,
    total_trades,
    realized_pnl,
    CASE 
        WHEN total_trades > 0 THEN realized_pnl / total_trades
        ELSE 0
    END as avg_pnl_per_trade
FROM daily_stats
ORDER BY trade_date DESC, account_id;

-- Create view for position summary
CREATE VIEW position_summary AS
SELECT 
    p.account_id,
    p.symbol,
    p.quantity,
    p.avg_price,
    p.unrealized_pnl,
    p.realized_pnl,
    m.last_price,
    (p.quantity * m.last_price) as market_value,
    p.updated_at
FROM positions p
LEFT JOIN market_data m ON p.symbol = m.symbol
WHERE p.quantity != 0;

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO trader;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO trader;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO trader;

-- Comments
COMMENT ON TABLE orders IS 'Order lifecycle tracking';
COMMENT ON TABLE trades IS 'Executed trades';
COMMENT ON TABLE positions IS 'Current positions by account and symbol';
COMMENT ON TABLE risk_limits IS 'Risk management limits per account';
COMMENT ON TABLE audit_log IS 'Audit trail for all trading activities';
COMMENT ON TABLE market_data IS 'Reference market data for symbols';

-- Made with Bob
