package com.example.kryo.runner;

import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import com.example.kryo.service.LettuceKryoDirectService;
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

    public DemoRunner(LettuceKryoDirectService lettuceKryoService) {
        this.lettuceKryoService = lettuceKryoService;
    }

    @Override
    public void run(String... args) {
        System.out.println("\n================================================================================");
        System.out.println(">>> STARTING DIRECT LETTUCE + KRYO 5 DEMONSTRATION <<<");
        System.out.println("================================================================================\n");

        try {
            // -------------------------------------------------------------------------
            // 1. Prepare sample domain objects
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
            // 2. DEMO 1: Synchronous Direct Lettuce Storage & Retrieval (UserProfile)
            // -------------------------------------------------------------------------
            System.out.println("--- [TEST 1] Direct Lettuce Synchronous SET / GET (UserProfile) ---");
            String userKey = "lettuce:user:" + user.getId();

            System.out.println("1. Storing UserProfile into Redis at key: " + userKey);
            lettuceKryoService.set(userKey, user);

            System.out.println("2. Retrieving UserProfile from Redis via Lettuce...");
            UserProfile retrievedUser = lettuceKryoService.get(userKey, UserProfile.class);

            System.out.println("   -> Retrieved Object: " + retrievedUser);
            boolean userEquals = user.equals(retrievedUser);
            System.out.println("   -> Object Equality Verified: " + (userEquals ? "✓ PASSED (Exact Match)" : "✗ FAILED"));

            // Inspect the raw binary stored in Redis
            byte[] userRawBytes = lettuceKryoService.getRawBytes(userKey);
            System.out.println("   -> Raw binary size in Redis: " + userRawBytes.length + " bytes");
            System.out.println("   -> Raw binary hex preview: " + HexFormat.of().formatHex(userRawBytes, 0, Math.min(32, userRawBytes.length)) + "...");

            // -------------------------------------------------------------------------
            // 3. DEMO 2: Synchronous Direct Lettuce Storage & Retrieval (Order)
            // -------------------------------------------------------------------------
            System.out.println("\n--- [TEST 2] Direct Lettuce Synchronous SET / GET (Order with BigDecimal & Dates) ---");
            String orderKey = "lettuce:order:" + order.getOrderId();

            System.out.println("1. Storing Order into Redis at key: " + orderKey);
            lettuceKryoService.set(orderKey, order);

            System.out.println("2. Retrieving Order from Redis via Lettuce...");
            Order retrievedOrder = lettuceKryoService.get(orderKey, Order.class);

            System.out.println("   -> Retrieved Order ID: " + retrievedOrder.getOrderId());
            System.out.println("   -> Retrieved Items Count: " + retrievedOrder.getItems().size());
            System.out.println("   -> Retrieved Total: $" + retrievedOrder.getTotalAmount());
            boolean orderEquals = order.equals(retrievedOrder);
            System.out.println("   -> Object Equality Verified: " + (orderEquals ? "✓ PASSED (Exact Match)" : "✗ FAILED"));

            // -------------------------------------------------------------------------
            // 4. DEMO 3: Asynchronous Non-blocking Operations with Lettuce + Kryo
            // -------------------------------------------------------------------------
            System.out.println("\n--- [TEST 3] Direct Lettuce Non-blocking Async Pipeline ---");
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
            System.out.println(">>> DIRECT LETTUCE + KRYO DEMONSTRATIONS COMPLETED SUCCESSFULLY! <<<");
            System.out.println("================================================================================\n");

        } catch (Exception e) {
            log.error("Error during DemoRunner execution: Redis may not be running or reachable.", e);
            System.err.println("Note: Start Redis using './start-redis.sh' or docker to run the demo against a live Redis instance.");
        }
    }
}
