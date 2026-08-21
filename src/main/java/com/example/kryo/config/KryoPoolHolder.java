package com.example.kryo.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Thread-safe Kryo instance pool and serialization helper.
 * Kryo instances are not thread-safe, so pooling guarantees safe concurrent access
 * while maximizing performance by reusing Kryo instances and buffers.
 *
 * <p>Configuration is externalized via application.yml under the {@code kryo.*} namespace:
 * <ul>
 *   <li>{@code kryo.pool-size} — max pooled Kryo instances (default 64)</li>
 *   <li>{@code kryo.output-buffer-size} — initial Output buffer in bytes (default 16384)</li>
 *   <li>{@code kryo.schema-version} — version byte prepended to every payload (default 1)</li>
 * </ul>
 */
@Component
public class KryoPoolHolder {

    private final Pool<Kryo> kryoPool;
    private final int outputBufferSize;
    private final byte schemaVersion;

    public KryoPoolHolder(@Value("${kryo.pool-size:64}") int poolSize,
                          @Value("${kryo.output-buffer-size:16384}") int outputBufferSize,
                          @Value("${kryo.schema-version:1}") int schemaVersion) {
        this.outputBufferSize = outputBufferSize;
        this.schemaVersion = (byte) schemaVersion;

        // Pool with maximum capacity and dynamic expansion
        this.kryoPool = new Pool<Kryo>(true, false, poolSize) {
            @Override
            protected Kryo create() {
                Kryo kryo = new Kryo();
                // Enforce strict whitelist security mode to prevent deserialization vulnerabilities (CVEs / RCE)
                kryo.setRegistrationRequired(true);
                // Support circular references and duplicate object graph references
                kryo.setReferences(true);

                // Add JDK 8+ java.time serializers (Instant, LocalDateTime, LocalDate, etc.)
                com.esotericsoftware.kryo.serializers.TimeSerializers.addDefaultSerializers(kryo);

                // 1. Domain Models (Assigned fixed IDs: 10-19)
                kryo.register(UserProfile.class, 10);
                kryo.register(Order.class, 11);
                kryo.register(OrderItem.class, 12);

                // 2. Common Java Standard Types (Assigned fixed IDs: 20-29)
                kryo.register(BigDecimal.class, new com.esotericsoftware.kryo.serializers.DefaultSerializers.BigDecimalSerializer(), 20);
                kryo.register(Instant.class, 21);
                kryo.register(LocalDateTime.class, 22);
                kryo.register(java.time.LocalDate.class, 23);
                kryo.register(java.time.LocalTime.class, 24);
                kryo.register(java.util.UUID.class, new com.esotericsoftware.kryo.Serializer<java.util.UUID>() {
                    @Override
                    public void write(Kryo k, Output o, java.util.UUID u) {
                        o.writeLong(u.getMostSignificantBits());
                        o.writeLong(u.getLeastSignificantBits());
                    }

                    @Override
                    public java.util.UUID read(Kryo k, Input i, Class<? extends java.util.UUID> type) {
                        return new java.util.UUID(i.readLong(), i.readLong());
                    }
                }, 25);

                // 3. Mutable Java Collections (Assigned fixed IDs: 30-39)
                kryo.register(ArrayList.class, 30);
                kryo.register(java.util.LinkedList.class, 31);
                kryo.register(HashMap.class, 32);
                kryo.register(java.util.LinkedHashMap.class, 33);
                kryo.register(java.util.HashSet.class, 34);
                kryo.register(java.util.LinkedHashSet.class, 35);
                kryo.register(java.util.TreeMap.class, 36);
                kryo.register(java.util.TreeSet.class, 37);

                // 4. Arrays (Assigned fixed IDs: 40-49)
                kryo.register(String[].class, 40);
                kryo.register(Object[].class, 41);

                // 5. java.util.Collections Helpers (Assigned fixed IDs: 50-59)
                kryo.register(java.util.Collections.emptyList().getClass(), 50);
                kryo.register(java.util.Collections.emptyMap().getClass(), 51);
                kryo.register(java.util.Collections.emptySet().getClass(), 52);
                kryo.register(java.util.Collections.singletonList("").getClass(), 53);
                kryo.register(java.util.Collections.singletonMap("", "").getClass(), 54);
                kryo.register(java.util.Collections.singleton("").getClass(), 55);
                kryo.register(java.util.Arrays.asList("").getClass(), 56);

                // 6. Java 9+ Immutable Collections (List.of, Map.of, Set.of) (Assigned fixed IDs: 60-69)
                kryo.register(java.util.List.of().getClass(), 60);                  // List0
                kryo.register(java.util.List.of("a").getClass(), 61);               // List12
                kryo.register(java.util.List.of("a", "b", "c").getClass(), 62);    // ListN
                kryo.register(java.util.Set.of().getClass(), 63);                   // Set0
                kryo.register(java.util.Set.of("a").getClass(), 64);                // Set12
                kryo.register(java.util.Set.of("a", "b", "c").getClass(), 65);     // SetN
                kryo.register(java.util.Map.of().getClass(), 66);                   // Map0
                kryo.register(java.util.Map.of("k", "v").getClass(), 67);           // Map1
                kryo.register(java.util.Map.of("k1", "v1", "k2", "v2").getClass(), 68); // MapN

                return kryo;
            }
        };
    }

    /**
     * Serializes any Java object into a Kryo byte array.
     * The first byte is always the schema version tag for forward/backward compatibility.
     */
    public byte[] serialize(Object object) {
        if (object == null) {
            return new byte[0];
        }

        Kryo kryo = kryoPool.obtain();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Prepend schema version byte before Kryo payload
            baos.write(schemaVersion);
            try (Output output = new Output(baos, outputBufferSize)) {
                kryo.writeClassAndObject(output, object);
                output.flush();
            }
            return baos.toByteArray();
        } catch (KryoSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new KryoSerializationException("Kryo serialization failed for: " + object.getClass().getName(), e);
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Deserializes a Kryo byte array back into an Object.
     * Validates the schema version byte before attempting deserialization.
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        // Validate schema version prefix
        byte version = bytes[0];
        if (version != schemaVersion) {
            throw new KryoSerializationException.SchemaVersionMismatchException(schemaVersion, version);
        }

        Kryo kryo = kryoPool.obtain();
        try (Input input = new Input(bytes, 1, bytes.length - 1)) {
            return (T) kryo.readClassAndObject(input);
        } catch (KryoSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new KryoSerializationException("Kryo deserialization failed", e);
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Deserializes a Kryo byte array into a specific target class.
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        Object obj = deserialize(bytes);
        if (obj == null) {
            return null;
        }
        if (!clazz.isInstance(obj)) {
            throw new ClassCastException("Expected instance of " + clazz.getName() + " but got " + obj.getClass().getName());
        }
        return (T) obj;
    }

    public Pool<Kryo> getKryoPool() {
        return kryoPool;
    }

    public byte getSchemaVersion() {
        return schemaVersion;
    }
}
