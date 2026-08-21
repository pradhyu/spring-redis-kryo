package com.example.kryo.config;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Spring Data Redis Serializer backed by Kryo.
 * Can be used with RedisTemplate for transparent Kryo serialization of Redis values.
 */
public class KryoRedisSerializer<T> implements RedisSerializer<T> {

    private final KryoPoolHolder kryoPoolHolder;
    private final Class<T> targetType;

    public KryoRedisSerializer(KryoPoolHolder kryoPoolHolder) {
        this(kryoPoolHolder, null);
    }

    public KryoRedisSerializer(KryoPoolHolder kryoPoolHolder, Class<T> targetType) {
        this.kryoPoolHolder = kryoPoolHolder;
        this.targetType = targetType;
    }

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        try {
            return kryoPoolHolder.serialize(t);
        } catch (Exception e) {
            throw new SerializationException("Cannot serialize object of type: " + t.getClass().getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            if (targetType != null) {
                return kryoPoolHolder.deserialize(bytes, targetType);
            }
            return (T) kryoPoolHolder.deserialize(bytes);
        } catch (Exception e) {
            throw new SerializationException("Cannot deserialize byte array with Kryo", e);
        }
    }
}
