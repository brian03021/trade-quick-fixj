# Trading Platform Architecture

## System Architecture

### High-Level Design

The trading platform follows a microservices architecture with event-driven communication patterns. Each service is independently deployable and scalable.

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ FIX Clients  │  │ REST Clients │  │  WebSocket   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
          │ FIX 4.4         │ HTTP/REST        │ WS
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    Gateway Layer                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              FIX Gateway (QuickFIX/J)                │   │
│  │  • Session Management  • Message Validation          │   │
│  │  • Protocol Conversion • Heartbeat Handling          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   Event Streaming Layer                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    Apache Kafka                       │   │
│  │  Topics: order.*, trade.*, risk.*, audit.*          │   │
│  └──────────────────────────────────────────────────────┘   │
└───┬─────────────────┬─────────────────┬───────────────┬─────┘
    │                 │                 │               │
    ▼                 ▼                 ▼               ▼
┌─────────┐    ┌─────────┐    ┌──────────────┐  ┌──────────┐
│ Order   │    │  Risk   │    │  Matching    │  │  Audit   │
│ Service │    │ Engine  │    │   Engine     │  │ Service  │
└────┬────┘    └────┬────┘    └──────┬───────┘  └────┬─────┘
     │              │                │               │
     └──────────────┴────────────────┴───────────────┘
                          │
     ┌────────────────────┼────────────────────┐
     │                    │                    │
     ▼                    ▼                    ▼
┌──────────┐      ┌──────────┐        ┌──────────────┐
│  Redis   │      │  Kafka   │        │ PostgreSQL   │
│  Cache   │      │  Log     │        │   Database   │
└──────────┘      └──────────┘        └──────────────┘
```

## Component Details

### 1. FIX Gateway

**Responsibility**: FIX protocol handling and session management

**Key Features**:
- QuickFIX/J 2.3.1 implementation
- FIX 4.4 protocol support
- Multi-session support
- Automatic reconnection
- Sequence number management
- Message validation

**Message Flow**:
```
Client → FIX Gateway → Validate → Convert → Kafka → Services
```

**Supported Messages**:
- NewOrderSingle (35=D)
- OrderCancelRequest (35=F)
- ExecutionReport (35=8)
- OrderCancelReject (35=9)
- Reject (35=3)

**Configuration**:
```ini
[SESSION]
BeginString=FIX.4.4
TargetCompID=CLIENT1
SocketAcceptPort=9876
HeartBtInt=30
```

### 2. Matching Engine

**Responsibility**: Order matching and trade execution

**Algorithm**: Price-Time Priority
```
1. Orders sorted by price (best price first)
2. Within same price, sorted by time (FIFO)
3. Incoming order matches against opposite side
4. Partial fills supported
5. Remaining quantity added to book
```

**Data Structures**:
```java
// Bids: Descending price order (highest first)
ConcurrentSkipListMap<BigDecimal, Queue<Order>> bids;

// Asks: Ascending price order (lowest first)
ConcurrentSkipListMap<BigDecimal, Queue<Order>> asks;

// O(log n) insert, delete, lookup
// Thread-safe with minimal locking
```

**Performance Characteristics**:
- Insert: O(log n)
- Delete: O(log n)
- Match: O(k) where k = number of matches
- Best bid/ask: O(1)

**Concurrency Model**:
```java
ReadWriteLock lock = new ReentrantReadWriteLock();

// Read operations (non-blocking)
lock.readLock().lock();
try {
    // Get best bid/ask
} finally {
    lock.readLock().unlock();
}

// Write operations (exclusive)
lock.writeLock().lock();
try {
    // Add/remove orders
} finally {
    lock.writeLock().unlock();
}
```

### 3. Order Service

**Responsibility**: Order lifecycle management

**State Machine**:
```
NEW → PARTIALLY_FILLED → FILLED
  ↓           ↓
CANCELLED   CANCELLED
  ↓
REJECTED
```

**Validation Rules**:
1. Duplicate order detection (client order ID)
2. Symbol validation
3. Price validation (limit orders)
4. Quantity validation (> 0)
5. Account validation

**Idempotency**:
```java
// Check Redis cache for duplicate
String key = "order:" + clientOrderId + ":" + accountId;
if (redis.exists(key)) {
    throw new DuplicateOrderException();
}
redis.setex(key, 3600, orderId); // 1 hour TTL
```

### 4. Risk Engine

**Responsibility**: Pre-trade risk checks

**Risk Checks**:
1. **Position Limits**
   ```java
   currentPosition + orderQuantity <= maxPositionSize
   ```

2. **Order Size Limits**
   ```java
   orderQuantity <= maxOrderSize
   ```

3. **Daily Loss Limits**
   ```java
   dailyPnL >= -maxDailyLoss
   ```

4. **Daily Volume Limits**
   ```java
   dailyVolume + orderQuantity <= maxDailyVolume
   ```

**Caching Strategy**:
```java
// Cache risk limits (5 min TTL)
RiskLimit limits = redis.get("risk:" + accountId);

// Cache positions (no expiry, updated on trades)
Position position = redis.get("position:" + accountId + ":" + symbol);
```

## Data Flow

### Order Submission Flow

```
1. Client sends NewOrderSingle (FIX)
   ↓
2. FIX Gateway validates message
   ↓
3. Convert to domain Order object
   ↓
4. Publish to Kafka (order.new)
   ↓
5. Order Service consumes event
   ↓
6. Check for duplicates (Redis)
   ↓
7. Publish to Kafka (order.validated)
   ↓
8. Risk Engine consumes event
   ↓
9. Perform risk checks
   ↓
10. If approved: Publish (order.approved)
    If rejected: Publish (order.rejected)
   ↓
11. Matching Engine consumes approved orders
   ↓
12. Match against order book
   ↓
13. If matched: Create trades, publish (trade.executed)
    If not matched: Add to book
   ↓
14. Update positions (Redis + PostgreSQL)
   ↓
15. Send ExecutionReport to client (FIX)
```

### Trade Execution Flow

```
Incoming Order (Buy 100 @ $150)
         ↓
Check Order Book (Asks)
         ↓
Best Ask: $149.50 (50 shares)
         ↓
Match 50 @ $149.50
         ↓
Create Trade:
  - Trade ID: TRD-xxx
  - Buy Order: ORD-xxx
  - Sell Order: ORD-yyy
  - Price: $149.50
  - Quantity: 50
         ↓
Update Orders:
  - Buy: 50 filled, 50 remaining
  - Sell: 50 filled, 0 remaining (FILLED)
         ↓
Publish trade.executed event
         ↓
Continue matching remaining 50 shares
```

## Event Schema

### Order Events

```json
{
  "eventId": "EVT-uuid",
  "orderId": "ORD-uuid",
  "type": "ORDER_ACCEPTED",
  "order": {
    "orderId": "ORD-uuid",
    "clientOrderId": "CLIENT123",
    "accountId": "ACC001",
    "symbol": "AAPL",
    "side": "BUY",
    "type": "LIMIT",
    "price": 150.00,
    "quantity": 100,
    "filledQuantity": 0,
    "status": "NEW",
    "createdAt": "2024-06-12T08:00:00Z",
    "updatedAt": "2024-06-12T08:00:00Z"
  },
  "timestamp": "2024-06-12T08:00:00Z"
}
```

### Trade Events

```json
{
  "eventId": "EVT-uuid",
  "trade": {
    "tradeId": "TRD-uuid",
    "buyOrderId": "ORD-uuid-1",
    "sellOrderId": "ORD-uuid-2",
    "symbol": "AAPL",
    "price": 150.00,
    "quantity": 100,
    "executedAt": "2024-06-12T08:00:01Z"
  },
  "timestamp": "2024-06-12T08:00:01Z"
}
```

## Scalability Considerations

### Horizontal Scaling

**Stateless Services**:
- FIX Gateway: Multiple instances with load balancer
- Order Service: Kafka consumer groups
- Risk Engine: Kafka consumer groups

**Stateful Services**:
- Matching Engine: Partition by symbol
  ```
  Symbol AAPL → Matching Engine 1
  Symbol GOOGL → Matching Engine 2
  Symbol MSFT → Matching Engine 3
  ```

### Kafka Partitioning

```java
// Partition by symbol for order locality
ProducerRecord<String, Order> record = 
    new ProducerRecord<>("order.new", order.getSymbol(), order);
```

### Caching Strategy

**Hot Data (Redis)**:
- Active orders (1 hour TTL)
- Positions (no expiry)
- Risk limits (5 min TTL)
- Order book snapshots (1 min TTL)

**Cold Data (PostgreSQL)**:
- Historical orders
- Historical trades
- Audit logs
- Daily statistics

## Performance Optimization

### Java 21 Virtual Threads

```java
@Configuration
public class VirtualThreadConfig {
    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
```

**Benefits**:
- Millions of concurrent threads
- Reduced memory footprint
- Simplified async code
- Better resource utilization

### Lock-Free Algorithms

```java
// ConcurrentSkipListMap provides lock-free reads
public Optional<BigDecimal> getBestBid() {
    return Optional.ofNullable(bids.firstKey());
}

// Atomic operations
AtomicLong orderCount = new AtomicLong(0);
orderCount.incrementAndGet();
```

### Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

## Monitoring & Observability

### Metrics

**Business Metrics**:
- Orders received per second
- Orders matched per second
- Average matching latency
- Order book depth
- Active orders count

**System Metrics**:
- JVM heap usage
- GC pause time
- Thread count
- CPU usage
- Network I/O

**Custom Metrics**:
```java
@Component
public class TradingMetrics {
    private final Counter ordersReceived;
    private final Timer matchingLatency;
    
    public TradingMetrics(MeterRegistry registry) {
        this.ordersReceived = Counter.builder("orders.received")
            .tag("type", "total")
            .register(registry);
            
        this.matchingLatency = Timer.builder("matching.latency")
            .description("Order matching latency")
            .register(registry);
    }
}
```

### Distributed Tracing

```java
// Trace order flow across services
@NewSpan("process-order")
public void processOrder(Order order) {
    // Processing logic
}
```

## Security

### Authentication
- FIX session authentication
- API key authentication for REST
- JWT tokens for WebSocket

### Authorization
- Role-based access control (RBAC)
- Account-level permissions
- Symbol-level restrictions

### Encryption
- TLS 1.3 for all connections
- At-rest encryption for sensitive data
- Secrets management with Vault

### Audit
- All actions logged to audit_log table
- Immutable event log in Kafka
- Compliance reporting

## Disaster Recovery

### Backup Strategy
- PostgreSQL: Daily full backup + WAL archiving
- Kafka: Replication factor 3
- Redis: AOF persistence + RDB snapshots

### Recovery Procedures
1. Restore PostgreSQL from backup
2. Replay Kafka events from last checkpoint
3. Rebuild Redis cache from database
4. Restart services in order:
   - Infrastructure (Kafka, Redis, PostgreSQL)
   - Core services (Order, Risk, Matching)
   - Gateway services (FIX Gateway)

## Future Enhancements

### Phase 2
- [ ] Market data feed integration
- [ ] Advanced order types (IOC, FOK, GTD)
- [ ] Multi-leg orders
- [ ] Order routing logic

### Phase 3
- [ ] WebSocket API for real-time updates
- [ ] REST API for order management
- [ ] Admin dashboard
- [ ] Reporting engine

### Phase 4
- [ ] Machine learning for trade analytics
- [ ] Smart order routing
- [ ] Algorithmic trading support
- [ ] Market making capabilities

---

**Document Version**: 1.0  
**Last Updated**: 2024-06-12  
**Author**: Trading Platform Team