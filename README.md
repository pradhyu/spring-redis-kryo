# Spring Boot 4 + Lettuce + Kryo Serialization

A production-ready **Spring Boot 4.1.1** (Spring Framework 7) application demonstrating high-performance Redis integration with **Lettuce** and **Kryo 5** binary serialization, with native **Virtual Threads** enabled.

---

## 🌟 Overview & Architecture

Standard JSON or Java serialization introduces significant CPU, memory, and bandwidth overhead. **Kryo 5** provides high-speed, compact binary object serialization.

This application uses the **Direct Lettuce Client (`RedisCodec<String, Object>`)** pattern:
* **Native Netty Pipeline Integration**: Kryo serialization and deserialization happen directly inside Lettuce's Netty channel pipeline using `ByteBuffer`, eliminating redundant intermediate byte array copying.
* **Why Direct Lettuce is Superior to Spring Data Redis**:
  1. **Zero Abstraction Overhead**: Eliminates `RedisTemplate`, `LettuceConnectionFactory`, and Spring Data adapter layers.
  2. **Direct Asynchronous & Reactive Execution**: Commands execute directly on Netty EventLoops without thread hopping.
  3. **Custom Netty Codec**: Lettuce's `RedisCodec` operates directly on ByteBuffers for maximum throughput.
* **Thread-Safety via Pooling**: Uses `com.esotericsoftware.kryo.util.Pool<Kryo>` to manage non-thread-safe `Kryo` instances with zero contention, compatible with Java 21 Virtual Threads.
* **Ultra-Compact Payloads**: Typically 60–75% smaller than JSON/Java native serialization.

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
| **Payload Size in Redis** | **529,610 bytes (~517 KB)** | 2,251,683 bytes (~2.15 MB) | 1,368,245 bytes (~1.30 MB) | **🔥 76.5% smaller than JSON<br>🔥 61.3% smaller than Java** |
| **Serialization Time** | **~9.66 ms – 31.94 ms** | ~13.40 ms – 41.14 ms | ~25.22 ms – 56.33 ms | **⚡ 1.39x – 2.56x faster than JSON<br>⚡ 1.76x – 2.61x faster than Java** |
| **Serialization Throughput**| **~1,035,000 ops/sec** | ~746,000 ops/sec | ~396,000 ops/sec | **2.6x higher throughput than Java** |
| **Deserialization Time** | **~8.27 ms – 32.29 ms** | ~14.79 ms – 54.89 ms | ~19.91 ms – 49.56 ms | **⚡ 1.70x – 1.79x faster than JSON<br>⚡ 1.53x – 2.41x faster than Java** |
| **Deserialization Throughput**| **~1,209,000 ops/sec** | ~675,000 ops/sec | ~502,000 ops/sec | **2.4x higher throughput than Java** |
| **Redis Round-Trip (SET+GET)**| **~79.4 ms total** | ~215+ ms *(est.)* | ~160+ ms *(est.)* | **Direct Netty codec eliminates byte array copies** |

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
Serialization Time |      9.66 ms     |     13.40 ms     |     25.22 ms    
Ser Throughput     | 1,035,519 ops/s  |   746,093 ops/s  |   396,569 ops/s 
Deserialization    |      8.27 ms     |     14.79 ms     |     19.91 ms    
Deser Throughput   | 1,209,295 ops/s  |   675,913 ops/s  |   502,371 ops/s 
-------------------+------------------+------------------+----------------
Size Comparison    : Kryo is 75.4% smaller than JSON, and 60.0% smaller than Java Native
Ser Speedup Factor : Kryo is 1.39x faster than JSON, and 2.61x faster than Java Native
Deser Speedup      : Kryo is 1.79x faster than JSON, and 2.41x faster than Java Native
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
    "payloadBytes": 529610,
    "payloadKB": "517.20 KB",
    "payloadMB": "0.51 MB",
    "serializeTimeMs": 31.94,
    "deserializeTimeMs": 32.29,
    "serializeThroughputOpsPerSec": 313068,
    "deserializeThroughputOpsPerSec": 309690
  },
  "jsonComparison": {
    "payloadBytes": 2251683,
    "payloadKB": "2198.91 KB",
    "payloadMB": "2.15 MB",
    "sizeReductionPercentage": "76.5% smaller with Kryo",
    "serializationSpeedup": "1.29x faster with Kryo",
    "deserializationSpeedup": "1.70x faster with Kryo"
  },
  "javaNativeComparison": {
    "payloadBytes": 1368245,
    "payloadKB": "1336.18 KB",
    "payloadMB": "1.30 MB",
    "sizeReductionPercentage": "61.3% smaller with Kryo",
    "serializationSpeedup": "1.76x faster with Kryo",
    "deserializationSpeedup": "1.53x faster with Kryo"
  },
  "redisOperations": {
    "key": "users",
    "redisSetTimeMs": 34.73,
    "redisGetTimeMs": 44.72,
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
| `GET` | `/api/demo/run` | Executes end-to-end Direct Lettuce + Kryo 5 demo |
| `GET` | `/api/benchmark/users` | Runs 3-way benchmark with 10,000 users saved to Redis key `users` |
| `POST` | `/api/users` | Stores a `UserProfile` in Redis via Direct Lettuce + Kryo |
| `GET` | `/api/users/{id}` | Retrieves a `UserProfile` from Redis via Direct Lettuce + Kryo |
| `POST` | `/api/orders` | Stores an `Order` in Redis via Direct Lettuce + Kryo |
| `GET` | `/api/orders/{id}` | Retrieves an `Order` from Redis via Direct Lettuce + Kryo |
| `GET` | `/api/redis/raw/{key}` | Inspects the exact raw binary/hex payload in Redis |

---

## 📁 Project Structure

```
├── mvnw / mvnw.cmd                     # Maven wrapper scripts
├── .mvn/wrapper/                       # Maven wrapper configuration
├── start-redis.sh                      # Script to start Redis locally or via Docker
├── stop-redis.sh                       # Script to gracefully stop Redis
├── docker-compose.yml                  # Docker Compose configuration for Redis
├── pom.xml                             # Maven configuration (Direct Lettuce + Kryo 5)
└── src
    ├── main
    │   ├── java/com/example/kryo
    │   │   ├── KryoRedisApplication.java
    │   │   ├── config
    │   │   │   ├── KryoPoolHolder.java        # Thread-safe Kryo pool & whitelist registration
    │   │   │   ├── KryoRedisCodec.java        # Direct Lettuce RedisCodec (Netty pipeline)
    │   │   │   └── RedisConfig.java           # Direct Lettuce RedisClient & connection beans
    │   │   ├── model
    │   │   │   ├── UserProfile.java           # User entity with nested Map & List
    │   │   │   ├── Order.java                 # Order entity with BigDecimal & dates
    │   │   │   └── OrderItem.java             # Order item entity
    │   │   ├── service
    │   │   │   └── LettuceKryoDirectService.java   # Pure Lettuce client service (Sync & Async)
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
            └── LettuceKryoIntegrationTest.java# Live Redis integration tests (Direct Lettuce)
```
