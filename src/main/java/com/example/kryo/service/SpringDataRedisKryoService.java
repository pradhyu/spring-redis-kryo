package com.example.kryo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service demonstrating Spring Data RedisTemplate backed by Lettuce connection factory
 * and configured with KryoRedisSerializer.
 */
@Service
public class SpringDataRedisKryoService {

    private static final Logger log = LoggerFactory.getLogger(SpringDataRedisKryoService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public SpringDataRedisKryoService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Store object into Redis using RedisTemplate + Kryo.
     */
    public void set(String key, Object value) {
        log.info("[Spring RedisTemplate] Setting key='{}' with object type: {}", key, value.getClass().getSimpleName());
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * Store object into Redis with TTL.
     */
    public void set(String key, Object value, Duration timeout) {
        log.info("[Spring RedisTemplate] Setting key='{}' with TTL={}", key, timeout);
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    /**
     * Retrieve object from Redis using RedisTemplate + Kryo.
     */
    public Object get(String key) {
        log.info("[Spring RedisTemplate] Getting key='{}'", key);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Retrieve object cast to specific class.
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
     * Delete key.
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * Check if key exists.
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
