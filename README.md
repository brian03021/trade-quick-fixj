# FIX-Based Trading Platform

A production-grade, high-performance trading platform built with Java 21

## 🏗️ Architecture

### System Overview

```
┌─────────────┐
│ FIX Clients │
└──────┬──────┘
       │ FIX 4.4
       ▼
┌─────────────────┐
│  FIX Gateway    │ ◄─── QuickFIX/J
│  (Port 9876)    │
└────────┬────────┘
         │
         ▼
    ┌────────┐
    │ Kafka  │ ◄─── Event Streaming
    └───┬────┘
        │
        ├──────────────┬──────────────┐
        ▼              ▼              ▼
┌──────────────┐ ┌──────────┐ ┌─────────────┐
│ Order Service│ │   Risk   │ │  Matching   │
│              │ │  Engine  │ │   Engine    │
└──────────────┘ └──────────┘ └─────────────┘
        │              │              │
        └──────────────┴──────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
   ┌────────┐    ┌────────┐    ┌──────────┐
   │ Redis  │    │ Kafka  │    │PostgreSQL│
   │ Cache  │    │ Events │    │ Database │
   └────────┘    └────────┘    └──────────┘
```

### Key Components

#### 1. **FIX Gateway** (Port 8081, FIX Port 9876)
- QuickFIX/J implementation
- FIX 4.4 protocol support
- Session management and heartbeat handling
- Message validation and routing
- Supports: NewOrderSingle (35=D), OrderCancelRequest (35=F), ExecutionReport (35=8)

#### 2. **Matching Engine** (Port 8084)
- High-performance in-memory order book
- Price-time priority matching algorithm
- ConcurrentSkipListMap for O(log n) operations
- Lock-based concurrency control
- Real-time trade execution

#### 3. **Order Service** (Port 8082)
- Order lifecycle management
- Duplicate detection
- State transitions
- Idempotency handling

#### 4. **Risk Engine** (Port 8083)
- Pre-trade risk checks
- Position limit validation
- Order size verification
- Real-time position tracking

## 🚀 Technology Stack

### Core Technologies
- **Java 21** - Virtual Threads, Pattern Matching, Records
- **Spring Boot 3.2+** - Modern Spring framework
- **Maven** - Build and dependency management

### Messaging & Data
- **QuickFIX/J 2.3.1** - FIX protocol implementation
- **Apache Kafka 3.6** - Event streaming and audit logs
- **Redis 7.2** - High-performance caching
- **PostgreSQL 16** - Persistent storage

### Observability
- **Micrometer** - Metrics collection
- **Prometheus** - Metrics storage and querying
- **Grafana** - Visualization and dashboards

### Infrastructure
- **Docker & Docker Compose** - Containerization
- **Testcontainers** - Integration testing

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker & Docker Compose
- 8GB RAM minimum
- Ports available: 5432, 6379, 9092, 9876, 8081-8084, 9090, 3000

## 🛠️ Quick Start

### 1. Clone and Build

```bash
# Clone the repository
git clone <repository-url>
cd trading-platform

# Build all modules
mvn clean install
```

### 2. Start Infrastructure

```bash
# Start all services with Docker Compose
docker-compose up -d

# Check service health
docker-compose ps
```

### 3. Verify Services

```bash
# Check FIX Gateway
curl http://localhost:8081/actuator/health

# Check Matching Engine
curl http://localhost:8084/actuator/health

# View Prometheus metrics
open http://localhost:9090

# View Grafana dashboards
open http://localhost:3000
# Login: admin/admin
```

## 📊 Database Schema

### Core Tables

#### Orders
```sql
CREATE TABLE orders (
    order_id VARCHAR(50) PRIMARY KEY,
    client_order_id VARCHAR(50) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    price DECIMAL(18,6),
    quantity BIGINT NOT NULL,
    filled_quantity BIGINT DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

#### Trades
```sql
CREATE TABLE trades (
    trade_id VARCHAR(50) PRIMARY KEY,
    buy_order_id VARCHAR(50) NOT NULL,
    sell_order_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    price DECIMAL(18,6) NOT NULL,
    quantity BIGINT NOT NULL,
    executed_at TIMESTAMP NOT NULL
);
```

#### Positions
```sql
CREATE TABLE positions (
    account_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity BIGINT NOT NULL,
    avg_price DECIMAL(18,6) NOT NULL,
    unrealized_pnl DECIMAL(18,6),
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (account_id, symbol)
);
```

## 🔧 Configuration

### FIX Gateway Configuration

**quickfix.cfg**
```ini
[DEFAULT]
ConnectionType=acceptor
StartTime=00:00:00
EndTime=00:00:00
HeartBtInt=30
SenderCompID=TRADING_PLATFORM

[SESSION]
BeginString=FIX.4.4
TargetCompID=CLIENT1
SocketAcceptPort=9876
```

### Application Configuration

**application.yml**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  data:
    redis:
      host: localhost
      port: 6379

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

## 📈 Kafka Topics

| Topic | Purpose | Retention |
|-------|---------|-----------|
| `order.new` | New order submissions | 7 days |
| `order.accepted` | Accepted orders | 7 days |
| `order.rejected` | Rejected orders | 7 days |
| `order.cancelled` | Cancelled orders | 7 days |
| `order.updated` | Order updates | 7 days |
| `trade.executed` | Executed trades | 30 days |
| `risk.violation` | Risk violations | 30 days |

## 🎯 Key Features

### High Performance
- **Virtual Threads (Java 21)** - Efficient concurrency
- **In-Memory Order Book** - Microsecond latency
- **Lock-Free Algorithms** - Where applicable
- **Redis Caching** - Sub-millisecond lookups

### Reliability
- **Event Sourcing** - Complete audit trail
- **Idempotency** - Duplicate order detection
- **Circuit Breakers** - Fault tolerance
- **Health Checks** - Service monitoring

### Scalability
- **Horizontal Scaling** - Stateless services
- **Kafka Partitioning** - Parallel processing
- **Connection Pooling** - Resource optimization
- **Async Processing** - Non-blocking operations

## 📊 Monitoring & Metrics

### Prometheus Metrics

Access metrics at: `http://localhost:9090`

Key metrics:
- `matching.trades` - Number of trades executed
- `matching.latency` - Order matching latency
- `orders.received` - Orders received count
- `jvm.memory.used` - JVM memory usage
- `http.server.requests` - HTTP request metrics

### Grafana Dashboards

Access dashboards at: `http://localhost:3000`

Default credentials: `admin/admin`

Pre-configured dashboards:
- Trading Platform Overview
- Order Flow Metrics
- System Performance
- JVM Metrics

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Load Testing
```bash
# Use your preferred load testing tool
# Example with JMeter or Gatling
```

## 🔒 Security Considerations

### Production Deployment
- [ ] Enable TLS/SSL for FIX connections
- [ ] Implement authentication and authorization
- [ ] Encrypt sensitive data at rest
- [ ] Use secrets management (Vault, AWS Secrets Manager)
- [ ] Enable audit logging
- [ ] Implement rate limiting
- [ ] Set up network segmentation
- [ ] Regular security audits

## 📝 API Examples

### FIX Message Examples

#### New Order Single (35=D)
```
8=FIX.4.4|9=XXX|35=D|49=CLIENT1|56=TRADING_PLATFORM|
34=1|52=20240612-08:00:00|
11=ORDER123|55=AAPL|54=1|38=100|40=2|44=150.00|
10=XXX|
```

#### Execution Report (35=8)
```
8=FIX.4.4|9=XXX|35=8|49=TRADING_PLATFORM|56=CLIENT1|
34=1|52=20240612-08:00:01|
37=ORD-UUID|11=ORDER123|17=EXEC-UUID|
150=2|39=2|55=AAPL|54=1|38=100|44=150.00|
32=100|31=150.00|151=0|14=100|6=150.00|
10=XXX|
```

## 🏗️ Architecture Patterns

### Design Patterns Used
- **Event Sourcing** - All state changes as events
- **CQRS** - Separate read/write models
- **Repository Pattern** - Data access abstraction
- **Factory Pattern** - Object creation
- **Strategy Pattern** - Algorithm selection
- **Observer Pattern** - Event notifications

### Clean Architecture Principles
- **Dependency Inversion** - High-level modules independent
- **Single Responsibility** - One reason to change
- **Open/Closed** - Open for extension, closed for modification
- **Interface Segregation** - Client-specific interfaces

## 🚀 Performance Benchmarks

### Expected Performance
- **Order Matching Latency**: < 1ms (p99)
- **FIX Message Processing**: < 5ms (p99)
- **Throughput**: 10,000+ orders/second
- **Order Book Depth**: 1,000+ levels per side

### Optimization Techniques
- ConcurrentSkipListMap for order book
- Virtual threads for I/O operations
- Redis for hot data caching
- Kafka for async processing
- Connection pooling
- Batch processing where applicable

## 📚 Project Structure

```
trading-platform/
├── common/                    # Shared models and utilities
│   └── src/main/java/com/trading/common/
│       ├── model/            # Domain models
│       ├── event/            # Event definitions
│       ├── util/             # Utility classes
│       └── exception/        # Custom exceptions
│
├── fix-gateway/              # FIX protocol gateway
│   └── src/main/java/com/trading/fix/
│       ├── config/           # QuickFIX configuration
│       ├── handler/          # FIX message handlers
│       └── service/          # Business logic
│
├── matching-engine/          # Order matching engine
│   └── src/main/java/com/trading/matching/
│       └── engine/           # Matching logic
│
├── database/                 # Database schemas
│   └── init.sql             # PostgreSQL schema
│
├── monitoring/               # Monitoring configuration
│   └── prometheus.yml       # Prometheus config
│
└── docker-compose.yml        # Docker orchestration
```

## 🤝 Contributing

This is a portfolio project demonstrating production-grade Java engineering. For questions or suggestions, please open an issue.

## 📄 License

This project is for educational and portfolio purposes.

## 🎓 Learning Resources

### FIX Protocol
- [FIX Protocol Specification](https://www.fixtrading.org/)
- [QuickFIX/J Documentation](https://www.quickfixj.org/)

### Java 21 Features
- [Virtual Threads](https://openjdk.org/jeps/444)
- [Pattern Matching](https://openjdk.org/jeps/441)
- [Record Patterns](https://openjdk.org/jeps/440)

### Architecture
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- [CQRS](https://martinfowler.com/bliki/CQRS.html)

## 📞 Support

For issues or questions:
1. Check existing documentation
2. Review logs: `docker-compose logs -f [service-name]`
3. Check service health: `curl http://localhost:808X/actuator/health`
4. Review Prometheus metrics: `http://localhost:9090`

---

**Built with ❤️ using Java 21 and modern engineering practices**