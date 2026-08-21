package com.example.kryo.service;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Service demonstrating direct Lettuce Client usage with custom KryoRedisCodec.
 */
@Service
public class LettuceKryoDirectService {

    private static final Logger log = LoggerFactory.getLogger(LettuceKryoDirectService.class);

    private final StatefulRedisConnection<String, Object> connection;
    private final StatefulRedisConnection<byte[], byte[]> rawConnection;
    private final RedisCommands<String, Object> syncCommands;
    private final RedisAsyncCommands<String, Object> asyncCommands;

    public LettuceKryoDirectService(StatefulRedisConnection<String, Object> connection,
                                     StatefulRedisConnection<byte[], byte[]> rawConnection) {
        this.connection = connection;
        this.rawConnection = rawConnection;
        this.syncCommands = connection.sync();
        this.asyncCommands = connection.async();
    }

    /**
     * Store an object synchronously into Redis using Lettuce + Kryo.
     */
    public String set(String key, Object value) {
        log.debug("[Direct Lettuce] Setting key='{}' type={}", key, value.getClass().getSimpleName());
        return syncCommands.set(key, value);
    }

    /**
     * Store an object with TTL synchronously.
     */
    public String setEx(String key, long seconds, Object value) {
        log.debug("[Direct Lettuce] Setting key='{}' with TTL={}s", key, seconds);
        return syncCommands.setex(key, seconds, value);
    }

    /**
     * Retrieve an object synchronously from Redis using Lettuce + Kryo.
     */
    public Object get(String key) {
        log.debug("[Direct Lettuce] Getting key='{}'", key);
        return syncCommands.get(key);
    }

    /**
     * Retrieve and cast to specific type.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object val = get(key);
        if (val == null) return null;
        if (!clazz.isInstance(val)) {
            throw new ClassCastException("Expected " + clazz.getName() + " but got " + val.getClass().getName());
        }
        return (T) val;
    }

    /**
     * Store an object asynchronously using Lettuce + Kryo.
     */
    public CompletableFuture<String> setAsync(String key, Object value) {
        RedisFuture<String> future = asyncCommands.set(key, value);
        return future.toCompletableFuture();
    }

    /**
     * Retrieve an object asynchronously using Lettuce + Kryo.
     */
    public CompletableFuture<Object> getAsync(String key) {
        RedisFuture<Object> future = asyncCommands.get(key);
        return future.toCompletableFuture();
    }

    /**
     * Delete key.
     */
    public Long delete(String key) {
        return syncCommands.del(key);
    }

    /**
     * Check if key exists.
     */
    public Boolean exists(String key) {
        return syncCommands.exists(key) > 0;
    }

    /**
     * Inspect raw bytes directly stored in Redis to verify Kryo binary encoding.
     * Uses a shared, reused connection (no TCP connection leak).
     */
    public byte[] getRawBytes(String key) {
        return rawConnection.sync().get(key.getBytes(StandardCharsets.UTF_8));
    }
}
