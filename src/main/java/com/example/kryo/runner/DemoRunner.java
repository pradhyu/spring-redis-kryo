package com.example.kryo.runner;

import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import com.example.kryo.service.LettuceKryoDirectService;
import com.example.kryo.service.SpringDataRedisKryoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class DemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final LettuceKryoDirectService lettuceKryoService;
    private final SpringDataRedisKryoService springKryoService;

    public DemoRunner(LettuceKryoDirectService lettuceKryoService,
                      SpringDataRedisKryoService springKryoService) {
        this.lettuceKryoService = lettuceKryoService;
        this.springKryoService = springKryoService;
    }

    @Override
    public void run(String... args) {
        System.out.println("\n================================================================================");
        System.out.println(">>> STARTING LETTUCE + KRYO SERIALIZATION DEMONSTRATION <<<");
        System.out.println("================================================================================\n");

        try {
            // -------------------------------------------------------------------------
            // 1. Prepare sample complex objects
            // -------------------------------------------------------------------------
            UserProfile user = new UserProfile(
                    1001L,
                    "alex_coder",
                    "alex.coder@example.com",
                    true,
                    List.of("ROLE_USER", "ROLE_ADMIN", "ROLE_DEVELOPER"),
                    Map.of("theme", "dark", "notifications", "email", "timezone", "UTC"),
                    Instant.now()
            );

            Order order = new Order(
                    "ORD-9988-XYZ",
                    user.getId(),
                    List.of(
                            new OrderItem("PROD-001", "Mechanical Keyboard RGB", 1, new BigDecimal("149.99")),
                            new OrderItem("PROD-002", "Ultra-wide Monitor 34-inch", 2, new BigDecimal("499.50")),
                            new OrderItem("PROD-003", "Ergonomic Desk Mat", 3, new BigDecimal("24.00"))
                    ),
                    "CONFIRMED",
                    new BigDecimal("1222.99"),
                    LocalDateTime.now()
            );

            // -------------------------------------------------------------------------
            // 2. DEMO 1: Pure Lettuce Client with Custom KryoRedisCodec
            // -------------------------------------------------------------------------
            System.out.println("--- [TEST 1] Pure Lettuce Client with KryoRedisCodec ---");
            String lettuceKey = "lettuce:user:" + user.getId();

            System.out.println("1. Storing UserProfile into Redis at key: " + lettuceKey);
            lettuceKryoService.set(lettuceKey, user);

            System.out.println("2. Retrieving UserProfile from Redis via Lettuce...");
            UserProfile retrievedUser = lettuceKryoService.get(lettuceKey, UserProfile.class);

            System.out.println("   -> Retrieved Object: " + retrievedUser);
            boolean userEquals = user.equals(retrievedUser);
            System.out.println("   -> Object Equality Verified: " + (userEquals ? "✓ PASSED (Exact Match)" : "✗ FAILED"));

            // Inspect the raw binary stored in Redis
            byte[] rawBytes = lettuceKryoService.getRawBytes(lettuceKey);
            System.out.println("   -> Raw binary size in Redis: " + rawBytes.length + " bytes");
            System.out.println("   -> Raw binary hex preview: " + HexFormat.of().formatHex(rawBytes, 0, Math.min(32, rawBytes.length)) + "...");

            // -------------------------------------------------------------------------
            // 3. DEMO 2: Spring Data RedisTemplate + KryoRedisSerializer
            // -------------------------------------------------------------------------
            System.out.println("\n--- [TEST 2] Spring Data RedisTemplate + KryoRedisSerializer ---");
            String springKey = "spring:order:" + order.getOrderId();

            System.out.println("1. Storing Order into Redis at key: " + springKey);
            springKryoService.set(springKey, order);

            System.out.println("2. Retrieving Order from Redis via RedisTemplate...");
            Order retrievedOrder = springKryoService.get(springKey, Order.class);

            System.out.println("   -> Retrieved Order ID: " + retrievedOrder.getOrderId());
            System.out.println("   -> Retrieved Items Count: " + retrievedOrder.getItems().size());
            System.out.println("   -> Retrieved Total: $" + retrievedOrder.getTotalAmount());
            boolean orderEquals = order.equals(retrievedOrder);
            System.out.println("   -> Object Equality Verified: " + (orderEquals ? "✓ PASSED (Exact Match)" : "✗ FAILED"));

            // -------------------------------------------------------------------------
            // 4. DEMO 3: Asynchronous Operations with Lettuce + Kryo
            // -------------------------------------------------------------------------
            System.out.println("\n--- [TEST 3] Lettuce Asynchronous Non-blocking Kryo Operations ---");
            String asyncKey = "lettuce:async:order:" + order.getOrderId();

            CompletableFuture<Void> asyncChain = lettuceKryoService.setAsync(asyncKey, order)
                    .thenCompose(setResult -> {
                        System.out.println("   -> Async SET completed with status: " + setResult);
                        return lettuceKryoService.getAsync(asyncKey);
                    })
                    .thenAccept(result -> {
                        Order asyncRetrieved = (Order) result;
                        System.out.println("   -> Async GET completed. Order ID: " + asyncRetrieved.getOrderId());
                        System.out.println("   -> Async Object Match: " + (order.equals(asyncRetrieved) ? "✓ PASSED" : "✗ FAILED"));
                    });

            asyncChain.join();

            System.out.println("\n================================================================================");
            System.out.println(">>> ALL LETTUCE + KRYO SERIALIZATION DEMONSTRATIONS COMPLETED SUCCESSFULLY! <<<");
            System.out.println("================================================================================\n");

        } catch (Exception e) {
            log.error("Error during DemoRunner execution: Redis may not be running or reachable.", e);
            System.err.println("Note: Start Redis using './start-redis.sh' or docker to run the demo against a live Redis instance.");
        }
    }
}
