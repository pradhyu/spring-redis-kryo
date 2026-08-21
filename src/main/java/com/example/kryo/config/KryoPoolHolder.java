package com.example.kryo.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.example.kryo.model.Order;
import com.example.kryo.model.OrderItem;
import com.example.kryo.model.UserProfile;
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
 */
@Component
public class KryoPoolHolder {

    private final Pool<Kryo> kryoPool;

    public KryoPoolHolder() {
        // Pool with maximum capacity and dynamic expansion
        this.kryoPool = new Pool<Kryo>(true, false, 64) {
            @Override
            protected Kryo create() {
                Kryo kryo = new Kryo();
                // Allow serializing classes even if not explicitly registered
                kryo.setRegistrationRequired(false);
                // Support circular references and duplicate object graph references
                kryo.setReferences(true);

                // Pre-register domain models and common types for smaller binary size & faster performance
                kryo.register(UserProfile.class, 10);
                kryo.register(Order.class, 11);
                kryo.register(OrderItem.class, 12);
                kryo.register(BigDecimal.class, 13);
                kryo.register(Instant.class, 14);
                kryo.register(LocalDateTime.class, 15);
                kryo.register(ArrayList.class, 16);
                kryo.register(HashMap.class, 17);

                return kryo;
            }
        };
    }

    /**
     * Serializes any Java object into a Kryo byte array.
     */
    public byte[] serialize(Object object) {
        if (object == null) {
            return new byte[0];
        }

        Kryo kryo = kryoPool.obtain();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos, 4096)) {
            kryo.writeClassAndObject(output, object);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo serialization failed for: " + object.getClass().getName(), e);
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Deserializes a Kryo byte array back into an Object.
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Kryo kryo = kryoPool.obtain();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             Input input = new Input(bais)) {
            return (T) kryo.readClassAndObject(input);
        } catch (Exception e) {
            throw new RuntimeException("Kryo deserialization failed", e);
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
}
