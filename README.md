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
# Run tests (including security and microbenchmark tests)
./mvnw clean test

# Run Spring Boot application
./mvnw spring-boot:run
```

On startup, `DemoRunner` executes automatically, storing complex domain models in Redis and verifying retrieval with Lettuce and Kryo.

---

## 🔒 Security Architecture (CVE & Deserialization Hardening)

Java deserialization attacks (**CWE-502 / RCE**) can exploit dynamic class loading if untrusted payloads contain gadget chains from libraries on the classpath.

This application is hardened with **Strict Whitelist Mode**:
* `kryo.setRegistrationRequired(true)`: Kryo rejects any class that has not been explicitly pre-registered.
* **Fixed Integer IDs**: Pre-registers domain models (`UserProfile`, `Order`, `OrderItem`), collections (`ArrayList`, `HashMap`, `List.of`, `Map.of`), and temporal types (`Instant`, `LocalDateTime`, `LocalDate`) with immutable integer IDs (10–69).
* Unregistered classes immediately throw an exception before instantiation, preventing remote code execution (RCE) vectors.

---

## 📊 Performance Benchmarking (10,000 Objects)

A comprehensive 3-way benchmark evaluates serializing and deserializing a dataset of **10,000 complex `UserProfile` objects** across **Kryo 5**, **Jackson JSON**, and **Java Native Serialization** (`ObjectOutputStream`/`ObjectInputStream`), stored in Redis under the key `users`.

### 3-Way Comparison Table

| Metric | ⚡ Kryo 5 (Binary) | 📄 Jackson JSON | ☕ Java Native Serialization | Kryo Advantage |
| :--- | :--- | :--- | :--- | :--- |
| **Payload Size in Redis** | **529,611 bytes (~517 KB)** | 2,251,683 bytes (~2.15 MB) | 1,368,245 bytes (~1.30 MB) | **🔥 76.5% smaller than JSON<br>🔥 61.3% smaller than Java** |
| **Serialization Time** | **~9.36 ms – 33.92 ms** | ~14.56 ms – 86.79 ms | ~24.84 ms – 69.21 ms | **⚡ 1.6x – 2.56x faster than JSON<br>⚡ 2.0x – 2.65x faster than Java** |
| **Serialization Throughput**| **~1,068,000 ops/sec** | ~686,000 ops/sec | ~402,000 ops/sec | **2.6x higher throughput than Java** |
| **Deserialization Time** | **~7.70 ms – 39.57 ms** | ~14.88 ms – 97.58 ms | ~20.08 ms – 58.45 ms | **⚡ 1.9x – 2.47x faster than JSON<br>⚡ 1.5x – 2.61x faster than Java** |
| **Deserialization Throughput**| **~1,299,000 ops/sec** | ~672,000 ops/sec | ~498,000 ops/sec | **2.6x higher throughput than Java** |
| **Redis Round-Trip (SET+GET)**| **~71.5 ms total** | ~215+ ms *(est.)* | ~160+ ms *(est.)* | **Drastically reduced I/O & latency** |

### Microbenchmark Output (`./mvnw test -Dtest=KryoBenchmarkTest`)

```text
==========================================================================
>>> BENCHMARK: 10,000 UserProfiles (Kryo vs Jackson JSON vs Java Native) <<<
==========================================================================
Dataset: 10,000 UserProfile objects
--------------------------------------------------------------------------
Metric             | Kryo 5 (Binary)  | Jackson JSON     | Java Native     
-------------------+------------------+------------------+----------------
Payload Size       |   559,610 bytes  | 2,271,683 bytes  | 1,398,244 bytes 
Payload KB         |    546.49 KB     |   2218.44 KB     |   1365.47 KB    
Payload MB         |      0.53 MB     |      2.17 MB     |      1.33 MB    
Serialization Time |      9.36 ms     |     14.56 ms     |     24.84 ms    
Ser Throughput     | 1,068,541 ops/s  |   686,933 ops/s  |   402,543 ops/s 
Deserialization    |      7.70 ms     |     14.88 ms     |     20.08 ms    
Deser Throughput   | 1,299,062 ops/s  |   671,999 ops/s  |   498,081 ops/s 
-------------------+------------------+------------------+----------------
Size Comparison    : Kryo is 75.4% smaller than JSON, and 60.0% smaller than Java Native
Ser Speedup Factor : Kryo is 1.56x faster than JSON, and 2.65x faster than Java Native
Deser Speedup      : Kryo is 1.93x faster than JSON, and 2.61x faster than Java Native
==========================================================================
```

### Live REST API Benchmark Output (`GET /api/benchmark/users`)

```bash
curl -s http://localhost:8080/api/benchmark/users | jq .
```

```json
{
  "status": "SUCCESS",
  "dataset": {
    "redisKey": "users",
    "totalCount": 10000,
    "objectType": "UserProfile"
  },
  "kryo": {
    "payloadBytes": 529611,
    "payloadKB": "517.20 KB",
    "payloadMB": "0.51 MB",
    "serializeTimeMs": 33.92,
    "deserializeTimeMs": 39.57,
    "serializeThroughputOpsPerSec": 294803,
    "deserializeThroughputOpsPerSec": 252737
  },
  "jsonComparison": {
    "payloadBytes": 2251683,
    "payloadKB": "2198.91 KB",
    "payloadMB": "2.15 MB",
    "sizeReductionPercentage": "76.5% smaller with Kryo",
    "serializationSpeedup": "2.56x faster with Kryo",
    "deserializationSpeedup": "2.47x faster with Kryo"
  },
  "javaNativeComparison": {
    "payloadBytes": 1368245,
    "payloadKB": "1336.18 KB",
    "payloadMB": "1.30 MB",
    "sizeReductionPercentage": "61.3% smaller with Kryo",
    "serializationSpeedup": "2.04x faster with Kryo",
    "deserializationSpeedup": "1.48x faster with Kryo"
  },
  "redisOperations": {
    "key": "users",
    "redisSetTimeMs": 36.84,
    "redisGetTimeMs": 34.72,
    "retrievedUsersCount": 10000,
    "roundTripVerified": true
  }
}
```

---

## 📡 REST API Endpoints

Once the application is running on `http://localhost:8080`:

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/demo/run` | Executes end-to-end Lettuce + Kryo & Spring Data demo |
| `GET` | `/api/benchmark/users` | Runs 3-way benchmark with 10,000 users saved to Redis key `users` |
| `POST` | `/api/users` | Stores a `UserProfile` in Redis via Lettuce + Kryo |
| `GET` | `/api/users/{id}` | Retrieves a `UserProfile` from Redis via Lettuce + Kryo |
| `POST` | `/api/orders` | Stores an `Order` in Redis via Spring RedisTemplate + Kryo |
| `GET` | `/api/orders/{id}` | Retrieves an `Order` from Redis via Spring RedisTemplate + Kryo |
| `GET` | `/api/redis/raw/{key}` | Inspects the exact raw binary/hex payload in Redis |

---

## 📁 Project Structure

```
├── mvnw / mvnw.cmd                     # Maven wrapper scripts
├── .mvn/wrapper/                       # Maven wrapper configuration
├── start-redis.sh                      # Script to start Redis locally or via Docker
├── stop-redis.sh                       # Script to gracefully stop Redis
├── docker-compose.yml                  # Docker Compose configuration for Redis
├── pom.xml                             # Maven configuration
└── src
    ├── main
    │   ├── java/com/example/kryo
    │   │   ├── KryoRedisApplication.java
    │   │   ├── config
    │   │   │   ├── KryoPoolHolder.java        # Thread-safe Kryo pool & whitelist registration
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
    │   │   │   └── DemoController.java        # REST API & Benchmark endpoints
    │   │   └── runner
    │   │       └── DemoRunner.java            # Startup demo runner
    │   └── resources
    │       └── application.yml
    └── test
        └── java/com/example/kryo
            ├── KryoSerializationTest.java     # Unit tests & CVE whitelist security tests
            ├── KryoBenchmarkTest.java         # 10,000 objects 3-way microbenchmark
            └── LettuceKryoIntegrationTest.java# Live Redis integration tests
```
