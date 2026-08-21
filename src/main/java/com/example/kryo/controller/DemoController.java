package com.example.kryo.controller;

import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import com.example.kryo.service.LettuceKryoDirectService;
import com.example.kryo.service.SpringDataRedisKryoService;
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
    private final SpringDataRedisKryoService springKryoService;

    public DemoController(LettuceKryoDirectService lettuceKryoService,
                          SpringDataRedisKryoService springKryoService) {
        this.lettuceKryoService = lettuceKryoService;
        this.springKryoService = springKryoService;
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

        // 2. Direct Lettuce store & retrieve
        String userKey = "demo:user:" + user.getId();
        lettuceKryoService.set(userKey, user);
        UserProfile retrievedUser = lettuceKryoService.get(userKey, UserProfile.class);

        // 3. Spring Data RedisTemplate store & retrieve
        String orderKey = "demo:order:" + order.getOrderId();
        springKryoService.set(orderKey, order);
        Order retrievedOrder = springKryoService.get(orderKey, Order.class);

        // 4. Raw bytes inspection
        byte[] userRawBytes = lettuceKryoService.getRawBytes(userKey);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "lettuceDirect", Map.of(
                        "key", userKey,
                        "stored", user,
                        "retrieved", retrievedUser,
                        "match", user.equals(retrievedUser),
                        "rawByteSize", userRawBytes != null ? userRawBytes.length : 0,
                        "rawHex", userRawBytes != null ? HexFormat.of().formatHex(userRawBytes) : ""
                ),
                "springDataRedis", Map.of(
                        "key", orderKey,
                        "stored", order,
                        "retrieved", retrievedOrder,
                        "match", order.equals(retrievedOrder)
                )
        ));
    }

    @PostMapping("/users")
    public ResponseEntity<String> saveUser(@RequestBody UserProfile user) {
        String key = "user:" + user.getId();
        lettuceKryoService.set(key, user);
        return ResponseEntity.ok("Saved UserProfile to Redis key: " + key);
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
        springKryoService.set(key, order);
        return ResponseEntity.ok("Saved Order to Redis key: " + key);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        String key = "order:" + id;
        Order order = springKryoService.get(key, Order.class);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
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
