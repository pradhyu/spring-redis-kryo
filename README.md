# Spring Boot + Lettuce + Kryo Serialization

A production-ready Spring Boot application demonstrating high-performance Redis integration with **Lettuce** and **Kryo 5** binary serialization.

---

## 🌟 Overview & Architecture

Standard JSON or Java serialization has significant overhead in terms of CPU cycles and memory size. **Kryo** is a fast, efficient binary object graph serialization framework for Java.

This project demonstrates two architectural patterns for integrating Lettuce and Kryo:
1. **Direct Lettuce Client (`RedisCodec<String, Object>`)**: Custom codec plugging Kryo directly into Lettuce's Netty pipeline for asynchronous, synchronous, and reactive Redis commands.
2. **Spring Data Redis (`RedisTemplate<String, Object>`)**: Custom `KryoRedisSerializer<T>` implementing Spring Data's `RedisSerializer` interface, backed by the `LettuceConnectionFactory`.

### Key Benefits of Kryo Serialization with Lettuce:
- **Thread-Safety via Pooling**: Uses `com.esotericsoftware.kryo.util.Pool<Kryo>` to manage non-thread-safe `Kryo` instances with zero contention.
- **Ultra-Compact Payloads**: Typically 50–70% smaller than JSON/Java serialization.
- **Dynamic & Pre-Registered Support**: Supports arbitrary Java types via dynamic class registration and optimized IDs for common models.
- **Complex Graphs & Java 8+ Date/Time**: Seamlessly handles `Instant`, `LocalDateTime`, `BigDecimal`, collections, and circular references.

---

## 🚀 Quick Start

### 1. Start Redis
Use the included script to start Redis (automatically detects local `redis-server` or Docker):
```bash
./start-redis.sh
```

To stop Redis later:
```bash
./stop-redis.sh
```

Alternatively, if you prefer Docker Compose:
```bash
docker compose up -d
```

### 2. Build and Run the Application
```bash
export JAVA_HOME=~/.local/opt/jdk-21.0.6+7/Contents/Home
export PATH="$HOME/.local/bin:$JAVA_HOME/bin:$PATH"

# Run tests
mvn clean test

# Run Spring Boot application
mvn spring-boot:run
```

On startup, `DemoRunner` executes automatically, storing complex domain models in Redis and verifying retrieval with Lettuce and Kryo.

---

## 📡 REST API Endpoints

Once the application is running on `http://localhost:8080`:

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/demo/run` | Executes an end-to-end demo and returns JSON result |
| `POST` | `/api/users` | Stores a `UserProfile` in Redis via Lettuce + Kryo |
| `GET` | `/api/users/{id}` | Retrieves a `UserProfile` from Redis via Lettuce + Kryo |
| `POST` | `/api/orders` | Stores an `Order` in Redis via Spring RedisTemplate + Kryo |
| `GET` | `/api/orders/{id}` | Retrieves an `Order` from Redis via Spring RedisTemplate + Kryo |
| `GET` | `/api/redis/raw/{key}` | Inspects the exact raw binary/hex payload in Redis |

---

## 📁 Project Structure

```
├── start-redis.sh                      # Script to start Redis locally or via Docker
├── stop-redis.sh                       # Script to gracefully stop Redis
├── docker-compose.yml                  # Docker Compose configuration for Redis
├── pom.xml                             # Maven configuration
└── src
    ├── main
    │   ├── java/com/example/kryo
    │   │   ├── KryoRedisApplication.java
    │   │   ├── config
    │   │   │   ├── KryoPoolHolder.java        # Thread-safe Kryo pool & helper
    │   │   │   ├── KryoRedisCodec.java        # Direct Lettuce RedisCodec
    │   │   │   ├── KryoRedisSerializer.java   # Spring RedisSerializer
    │   │   │   └── RedisConfig.java           # Lettuce connection factory & beans
    │   │   ├── model
    │   │   │   ├── UserProfile.java           # User entity with nested Map & List
    │   │   │   ├── Order.java                 # Order entity with BigDecimal & dates
    │   │   │   └── OrderItem.java             # Order item entity
    │   │   ├── service
    │   │   │   ├── LettuceKryoDirectService.java   # Pure Lettuce service
    │   │   │   └── SpringDataRedisKryoService.java # Spring RedisTemplate service
    │   │   ├── controller
    │   │   │   └── DemoController.java        # REST API for interactive testing
    │   │   └── runner
    │   │       └── DemoRunner.java            # Startup demo runner
    │   └── resources
    │       └── application.yml
    └── test
        └── java/com/example/kryo
            ├── KryoSerializationTest.java     # Unit tests for Kryo serialization
            └── LettuceKryoIntegrationTest.java# Live Redis integration tests
```
# spring-redis-kryo
