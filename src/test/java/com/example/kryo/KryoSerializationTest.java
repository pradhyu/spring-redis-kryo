package com.example.kryo;

import com.example.kryo.config.KryoPoolHolder;
import com.example.kryo.config.KryoRedisCodec;
import com.example.kryo.config.KryoSerializationException;
import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KryoSerializationTest {

    private KryoPoolHolder kryoPoolHolder;
    private KryoRedisCodec kryoRedisCodec;

    @BeforeEach
    void setUp() {
        kryoPoolHolder = new KryoPoolHolder(64, 16384, 1);
        kryoRedisCodec = new KryoRedisCodec(kryoPoolHolder);
    }

    @Test
    @DisplayName("Should serialize and deserialize UserProfile with KryoPoolHolder")
    void testUserProfileSerialization() {
        UserProfile user = new UserProfile(
                101L, "john_doe", "john@example.com", true,
                List.of("ADMIN", "USER"),
                Map.of("lang", "en", "timezone", "EST"),
                Instant.now()
        );

        byte[] bytes = kryoPoolHolder.serialize(user);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        UserProfile deserialized = kryoPoolHolder.deserialize(bytes, UserProfile.class);
        assertEquals(user, deserialized);
        assertEquals(user.getId(), deserialized.getId());
        assertEquals(user.getUsername(), deserialized.getUsername());
        assertEquals(user.getEmail(), deserialized.getEmail());
        assertEquals(user.getRoles(), deserialized.getRoles());
        assertEquals(user.getPreferences(), deserialized.getPreferences());
        assertEquals(user.getCreatedAt(), deserialized.getCreatedAt());
    }

    @Test
    @DisplayName("Should serialize and deserialize Order with nested OrderItems")
    void testOrderSerialization() {
        Order order = new Order(
                "ORD-12345",
                202L,
                List.of(
                        new OrderItem("ITEM-A", "Item A", 2, new BigDecimal("19.99")),
                        new OrderItem("ITEM-B", "Item B", 1, new BigDecimal("89.50"))
                ),
                "SHIPPED",
                new BigDecimal("129.48"),
                LocalDateTime.now()
        );

        byte[] bytes = kryoPoolHolder.serialize(order);
        assertNotNull(bytes);

        Order deserialized = kryoPoolHolder.deserialize(bytes, Order.class);
        assertEquals(order, deserialized);
        assertEquals(2, deserialized.getItems().size());
        assertEquals(new BigDecimal("129.48"), deserialized.getTotalAmount());
    }

    @Test
    @DisplayName("Should encode and decode value with Lettuce KryoRedisCodec")
    void testLettuceKryoCodec() {
        UserProfile user = new UserProfile(
                555L, "lettuce_user", "lettuce@spring.io", true,
                List.of("ROLE_USER"),
                Map.of("theme", "auto"),
                Instant.now()
        );

        ByteBuffer encoded = kryoRedisCodec.encodeValue(user);
        assertNotNull(encoded);
        assertTrue(encoded.hasRemaining());

        Object decoded = kryoRedisCodec.decodeValue(encoded);
        assertNotNull(decoded);
        assertInstanceOf(UserProfile.class, decoded);
        assertEquals(user, decoded);
    }

    @Test
    @DisplayName("Should handle null and empty values gracefully")
    void testNullHandling() {
        byte[] serialized = kryoPoolHolder.serialize(null);
        assertEquals(0, serialized.length);

        assertNull(kryoPoolHolder.deserialize(null));
        assertNull(kryoPoolHolder.deserialize(new byte[0]));

        ByteBuffer buffer = kryoRedisCodec.encodeValue(null);
        assertEquals(0, buffer.remaining());
        assertNull(kryoRedisCodec.decodeValue(null));
    }

    // Custom unregistered dummy class for security test
    static class UnregisteredMaliciousPayload {
        private String command = "calc.exe";
    }

    @Test
    @DisplayName("Security: Should reject unregistered classes when registrationRequired is true")
    void testUnregisteredClassRejection() {
        UnregisteredMaliciousPayload payload = new UnregisteredMaliciousPayload();

        KryoSerializationException exception = assertThrows(
                KryoSerializationException.class,
                () -> kryoPoolHolder.serialize(payload),
                "Should throw KryoSerializationException for unregistered class"
        );

        assertNotNull(exception.getCause(), "Must have an underlying cause");
        assertTrue(
                exception.getCause().getMessage().contains("Class is not registered"),
                "Underlying cause message must state 'Class is not registered'"
        );
        assertTrue(
                exception.getMessage().contains("Kryo serialization failed"),
                "Error message should indicate serialization failure"
        );
    }

    @Test
    @DisplayName("Schema: First byte of serialized payload should be the schema version")
    void testSchemaVersionPrefix() {
        UserProfile user = new UserProfile(
                999L, "schema_test", "schema@test.io", true,
                List.of("ROLE_USER"), Map.of("key", "val"), Instant.now()
        );

        byte[] bytes = kryoPoolHolder.serialize(user);
        assertEquals(1, bytes[0], "First byte should be schema version 1");
    }

    @Test
    @DisplayName("Schema: Should reject payload with wrong schema version")
    void testSchemaVersionMismatch() {
        UserProfile user = new UserProfile(
                888L, "version_test", "v@test.io", true,
                List.of("ROLE_USER"), Map.of("k", "v"), Instant.now()
        );

        byte[] bytes = kryoPoolHolder.serialize(user);
        // Corrupt the schema version byte
        bytes[0] = 99;

        assertThrows(
                KryoSerializationException.SchemaVersionMismatchException.class,
                () -> kryoPoolHolder.deserialize(bytes),
                "Should throw SchemaVersionMismatchException for wrong version byte"
        );
    }

    @Test
    @DisplayName("Codec: Should reject null key with IllegalArgumentException")
    void testNullKeyRejection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> kryoRedisCodec.encodeKey(null),
                "Should throw IllegalArgumentException for null key"
        );
    }
}

