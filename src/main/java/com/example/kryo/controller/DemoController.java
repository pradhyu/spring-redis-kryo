package com.example.kryo.controller;

import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import com.example.kryo.service.BenchmarkService;
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
    private final BenchmarkService benchmarkService;

    public DemoController(LettuceKryoDirectService lettuceKryoService,
                          BenchmarkService benchmarkService) {
        this.lettuceKryoService = lettuceKryoService;
        this.benchmarkService = benchmarkService;
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
        return ResponseEntity.ok(benchmarkService.runBenchmark(count));
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
