A production-grade FIX-based trading platform 

Phase 1: Architecture

* Design full system architecture
* Include FIX Gateway (QuickFIX/J)
* Include Order Service, Risk Engine, Matching Engine
* Include Kafka + PostgreSQL + Redis
* Show message flow diagram

Phase 2: Core System

* Implement FIX Gateway using QuickFIX/J
* Support: NewOrderSingle (35=D), CancelOrder (35=F), ExecutionReport (35=8)

Phase 3: Trading Core

* Implement Order Book (price-time priority)
* Implement Risk checks (position, limits)
* Implement matching engine (in-memory, high performance)

Phase 4: Event System

* Use Kafka for persistence and audit logs
* Use Redis for caching positions/orders

Phase 5: Infrastructure

* Docker Compose setup
* PostgreSQL schema
* Basic monitoring (Prometheus metrics)

Constraints:

* Java 21
* Clean architecture
* High-performance design

