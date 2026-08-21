package com.example.kryo.controller;

import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import com.example.kryo.service.LettuceKryoDirectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final LettuceKryoDirectService lettuceKryoService;
    private final com.example.kryo.config.KryoPoolHolder kryoPoolHolder;

    public DemoController(LettuceKryoDirectService lettuceKryoService,
                          com.example.kryo.config.KryoPoolHolder kryoPoolHolder) {
        this.lettuceKryoService = lettuceKryoService;
        this.kryoPoolHolder = kryoPoolHolder;
    }

    @GetMapping("/demo/run")
    public ResponseEntity<Map<String, Object>> runDemo() {
        // 1. Create sample objects
        UserProfile user = new UserProfile(
                2001L, "redis_master", "master@redis.io", true,
                List.of("ADMIN", "ENGINEER"),
                Map.of("theme", "midnight-blue", "notifications", "sms"),
                Instant.now()
        );

        Order order = new Order(
                "ORD-REST-555",
                user.getId(),
                List.of(
                        new OrderItem("ITEM-1", "Lettuce Client Guide", 1, new BigDecimal("29.99")),
                        new OrderItem("ITEM-2", "Kryo High Performance Serializer", 2, new BigDecimal("49.99"))
                ),
                "PROCESSING",
                new BigDecimal("129.97"),
                LocalDateTime.now()
        );

        // 2. Direct Lettuce store & retrieve (UserProfile)
        String userKey = "demo:user:" + user.getId();
        lettuceKryoService.set(userKey, user);
        UserProfile retrievedUser = lettuceKryoService.get(userKey, UserProfile.class);

        // 3. Direct Lettuce store & retrieve (Order)
        String orderKey = "demo:order:" + order.getOrderId();
        lettuceKryoService.set(orderKey, order);
        Order retrievedOrder = lettuceKryoService.get(orderKey, Order.class);

        // 4. Raw bytes inspection
        byte[] userRawBytes = lettuceKryoService.getRawBytes(userKey);
        byte[] orderRawBytes = lettuceKryoService.getRawBytes(orderKey);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "lettuceDirectUser", Map.of(
                        "key", userKey,
                        "stored", user,
                        "retrieved", retrievedUser,
                        "match", user.equals(retrievedUser),
                        "rawByteSize", userRawBytes != null ? userRawBytes.length : 0,
                        "rawHex", userRawBytes != null ? HexFormat.of().formatHex(userRawBytes) : ""
                ),
                "lettuceDirectOrder", Map.of(
                        "key", orderKey,
                        "stored", order,
                        "retrieved", retrievedOrder,
                        "match", order.equals(retrievedOrder),
                        "rawByteSize", orderRawBytes != null ? orderRawBytes.length : 0,
                        "rawHex", orderRawBytes != null ? HexFormat.of().formatHex(orderRawBytes) : ""
                )
        ));
    }

    @PostMapping("/users")
    public ResponseEntity<String> saveUser(@RequestBody UserProfile user) {
        String key = "user:" + user.getId();
        lettuceKryoService.set(key, user);
        return ResponseEntity.ok("Saved UserProfile via Direct Lettuce to Redis key: " + key);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserProfile> getUser(@PathVariable Long id) {
        String key = "user:" + id;
        UserProfile user = lettuceKryoService.get(key, UserProfile.class);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/orders")
    public ResponseEntity<String> saveOrder(@RequestBody Order order) {
        String key = "order:" + order.getOrderId();
        lettuceKryoService.set(key, order);
        return ResponseEntity.ok("Saved Order via Direct Lettuce to Redis key: " + key);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        String key = "order:" + id;
        Order order = lettuceKryoService.get(key, Order.class);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/benchmark/users")
    public ResponseEntity<Map<String, Object>> benchmarkUsers(@RequestParam(defaultValue = "10000") int count) {
        // 1. Generate N user profiles
        List<UserProfile> users = new java.util.ArrayList<>(count);
        Instant now = Instant.now();
        for (int i = 1; i <= count; i++) {
            users.add(new UserProfile(
                    (long) i,
                    "user_" + i,
                    "user" + i + "@enterprise.io",
                    i % 2 == 0,
                    List.of("ROLE_USER", "ROLE_ENGINEER"),
                    Map.of("theme", "dark", "locale", "en_US", "notifications", "email"),
                    now
            ));
        }

        // 2. Measure Kryo Serialization
        long startKryoSer = System.nanoTime();
        byte[] kryoBytes = kryoPoolHolder.serialize(users);
        long durationKryoSerNs = System.nanoTime() - startKryoSer;
        double durationKryoSerMs = durationKryoSerNs / 1_000_000.0;

        // 3. Measure Kryo Deserialization
        long startKryoDeser = System.nanoTime();
        List<?> deserializedKryo = kryoPoolHolder.deserialize(kryoBytes, java.util.ArrayList.class);
        long durationKryoDeserNs = System.nanoTime() - startKryoDeser;
        double durationKryoDeserMs = durationKryoDeserNs / 1_000_000.0;

        // 4. Measure Jackson JSON Serialization & Deserialization
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        byte[] jsonBytes = new byte[0];
        double durationJsonSerMs = 0;
        double durationJsonDeserMs = 0;
        try {
            long startJsonSer = System.nanoTime();
            jsonBytes = mapper.writeValueAsBytes(users);
            durationJsonSerMs = (System.nanoTime() - startJsonSer) / 1_000_000.0;

            long startJsonDeser = System.nanoTime();
            mapper.readValue(jsonBytes, new com.fasterxml.jackson.core.type.TypeReference<List<UserProfile>>() {});
            durationJsonDeserMs = (System.nanoTime() - startJsonDeser) / 1_000_000.0;
        } catch (Exception ignored) {
        }

        // 5. Measure Java Native Serialization (ObjectOutputStream / ObjectInputStream)
        byte[] javaBytes = new byte[0];
        double durationJavaSerMs = 0;
        double durationJavaDeserMs = 0;
        try {
            long startJavaSer = System.nanoTime();
            java.io.ByteArrayOutputStream javaBaos = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(javaBaos)) {
                oos.writeObject(users);
            }
            javaBytes = javaBaos.toByteArray();
            durationJavaSerMs = (System.nanoTime() - startJavaSer) / 1_000_000.0;

            long startJavaDeser = System.nanoTime();
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(javaBytes))) {
                ois.readObject();
            }
            durationJavaDeserMs = (System.nanoTime() - startJavaDeser) / 1_000_000.0;
        } catch (Exception ignored) {
        }

        // 6. Store 10,000 users into Redis at key 'users' via Lettuce Kryo
        String redisKey = "users";
        long startRedisSet = System.nanoTime();
        lettuceKryoService.set(redisKey, users);
        double redisSetMs = (System.nanoTime() - startRedisSet) / 1_000_000.0;

        // 7. Retrieve 10,000 users from Redis at key 'users' via Lettuce Kryo
        long startRedisGet = System.nanoTime();
        List<?> retrievedUsers = (List<?>) lettuceKryoService.get(redisKey);
        double redisGetMs = (System.nanoTime() - startRedisGet) / 1_000_000.0;

        // 8. Calculate comparisons
        double vsJsonSizeSavings = jsonBytes.length > 0 ? (1.0 - ((double) kryoBytes.length / jsonBytes.length)) * 100.0 : 0;
        double vsJavaSizeSavings = javaBytes.length > 0 ? (1.0 - ((double) kryoBytes.length / javaBytes.length)) * 100.0 : 0;

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "dataset", Map.of(
                        "objectType", "UserProfile",
                        "totalCount", count,
                        "redisKey", redisKey
                ),
                "kryo", Map.of(
                        "payloadBytes", kryoBytes.length,
                        "payloadKB", String.format("%.2f KB", kryoBytes.length / 1024.0),
                        "payloadMB", String.format("%.2f MB", kryoBytes.length / (1024.0 * 1024.0)),
                        "serializeTimeMs", Double.parseDouble(String.format("%.2f", durationKryoSerMs)),
                        "deserializeTimeMs", Double.parseDouble(String.format("%.2f", durationKryoDeserMs)),
                        "serializeThroughputOpsPerSec", (long) (count / (durationKryoSerMs / 1000.0)),
                        "deserializeThroughputOpsPerSec", (long) (count / (durationKryoDeserMs / 1000.0))
                ),
                "jsonComparison", Map.of(
                        "payloadBytes", jsonBytes.length,
                        "payloadKB", String.format("%.2f KB", jsonBytes.length / 1024.0),
                        "payloadMB", String.format("%.2f MB", jsonBytes.length / (1024.0 * 1024.0)),
                        "serializeTimeMs", Double.parseDouble(String.format("%.2f", durationJsonSerMs)),
                        "deserializeTimeMs", Double.parseDouble(String.format("%.2f", durationJsonDeserMs)),
                        "sizeReductionPercentage", String.format("%.1f%% smaller with Kryo", vsJsonSizeSavings),
                        "serializationSpeedup", String.format("%.2fx faster with Kryo", durationJsonSerMs / durationKryoSerMs),
                        "deserializationSpeedup", String.format("%.2fx faster with Kryo", durationJsonDeserMs / durationKryoDeserMs)
                ),
                "javaNativeComparison", Map.of(
                        "payloadBytes", javaBytes.length,
                        "payloadKB", String.format("%.2f KB", javaBytes.length / 1024.0),
                        "payloadMB", String.format("%.2f MB", javaBytes.length / (1024.0 * 1024.0)),
                        "serializeTimeMs", Double.parseDouble(String.format("%.2f", durationJavaSerMs)),
                        "deserializeTimeMs", Double.parseDouble(String.format("%.2f", durationJavaDeserMs)),
                        "sizeReductionPercentage", String.format("%.1f%% smaller with Kryo", vsJavaSizeSavings),
                        "serializationSpeedup", String.format("%.2fx faster with Kryo", durationJavaSerMs / durationKryoSerMs),
                        "deserializationSpeedup", String.format("%.2fx faster with Kryo", durationJavaDeserMs / durationKryoDeserMs)
                ),
                "redisOperations", Map.of(
                        "key", redisKey,
                        "redisSetTimeMs", Double.parseDouble(String.format("%.2f", redisSetMs)),
                        "redisGetTimeMs", Double.parseDouble(String.format("%.2f", redisGetMs)),
                        "retrievedUsersCount", retrievedUsers != null ? retrievedUsers.size() : 0,
                        "roundTripVerified", retrievedUsers != null && retrievedUsers.size() == count
                )
        ));
    }

    @GetMapping("/redis/raw/{key}")
    public ResponseEntity<Map<String, Object>> getRawBinary(@PathVariable String key) {
        byte[] bytes = lettuceKryoService.getRawBytes(key);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "key", key,
                "lengthBytes", bytes.length,
                "hex", HexFormat.of().formatHex(bytes),
                "base64", Base64.getEncoder().encodeToString(bytes)
        ));
    }
}
