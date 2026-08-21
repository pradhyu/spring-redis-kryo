package com.example.kryo;

import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import com.example.kryo.service.LettuceKryoDirectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LettuceKryoIntegrationTest {

    @Autowired
    private LettuceKryoDirectService lettuceKryoService;

    @Test
    @DisplayName("Test direct Lettuce client: Synchronous store and retrieve UserProfile using Kryo")
    void testDirectLettuceStoreAndRetrieveUser() {
        String key = "test:lettuce:user:" + UUID.randomUUID();
        UserProfile user = new UserProfile(
                999L,
                "integration_tester",
                "tester@domain.com",
                true,
                List.of("ROLE_QA", "ROLE_USER"),
                Map.of("browser", "chrome", "notifications", "enabled"),
                Instant.now()
        );

        // 1. Store via Direct Lettuce
        String setResult = lettuceKryoService.set(key, user);
        assertEquals("OK", setResult);

        // 2. Verify exists
        assertTrue(lettuceKryoService.exists(key));

        // 3. Retrieve via Direct Lettuce
        UserProfile retrieved = lettuceKryoService.get(key, UserProfile.class);
        assertNotNull(retrieved);
        assertEquals(user, retrieved);
        assertEquals(user.getUsername(), retrieved.getUsername());
        assertEquals(user.getPreferences(), retrieved.getPreferences());

        // 4. Verify raw binary payload in Redis
        byte[] rawBytes = lettuceKryoService.getRawBytes(key);
        assertNotNull(rawBytes);
        assertTrue(rawBytes.length > 0);

        // 5. Clean up
        lettuceKryoService.delete(key);
        assertFalse(lettuceKryoService.exists(key));
    }

    @Test
    @DisplayName("Test direct Lettuce client: Synchronous store and retrieve Order using Kryo")
    void testDirectLettuceStoreAndRetrieveOrder() {
        String key = "test:lettuce:order:" + UUID.randomUUID();
        Order order = new Order(
                "ORD-" + UUID.randomUUID(),
                888L,
                List.of(
                        new OrderItem("P1", "Spring Boot 4 in Action", 1, new BigDecimal("45.00")),
                        new OrderItem("P2", "Redis & Lettuce High Performance", 2, new BigDecimal("39.95"))
                ),
                "PLACED",
                new BigDecimal("124.90"),
                LocalDateTime.now()
        );

        // 1. Store via Direct Lettuce
        String setResult = lettuceKryoService.set(key, order);
        assertEquals("OK", setResult);
        assertTrue(lettuceKryoService.exists(key));

        // 2. Retrieve via Direct Lettuce
        Order retrieved = lettuceKryoService.get(key, Order.class);
        assertNotNull(retrieved);
        assertEquals(order, retrieved);
        assertEquals(order.getTotalAmount(), retrieved.getTotalAmount());
        assertEquals(2, retrieved.getItems().size());

        // 3. Clean up
        lettuceKryoService.delete(key);
        assertFalse(lettuceKryoService.exists(key));
    }

    @Test
    @DisplayName("Test direct Lettuce client: Asynchronous non-blocking operations with Kryo")
    void testAsyncLettuceOperations() throws Exception {
        String key = "test:lettuce:async:" + UUID.randomUUID();
        UserProfile user = new UserProfile(
                123L, "async_user", "async@io.com", true,
                List.of("ROLE_ASYNC"),
                Map.of("mode", "reactive"),
                Instant.now()
        );

        CompletableFuture<String> setFuture = lettuceKryoService.setAsync(key, user);
        assertEquals("OK", setFuture.get());

        CompletableFuture<Object> getFuture = lettuceKryoService.getAsync(key);
        Object retrieved = getFuture.get();
        assertNotNull(retrieved);
        assertEquals(user, retrieved);

        lettuceKryoService.delete(key);
        assertFalse(lettuceKryoService.exists(key));
    }
}
