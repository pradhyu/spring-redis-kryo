package com.example.kryo.config;

import io.lettuce.core.codec.RedisCodec;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Direct Lettuce RedisCodec implementation utilizing Kryo serialization.
 * Keys are encoded as UTF-8 Strings, and Values are serialized/deserialized
 * to/from binary using the thread-safe Kryo pool.
 */
@Component
public class KryoRedisCodec implements RedisCodec<String, Object> {

    private final KryoPoolHolder kryoPoolHolder;
    private final Charset charset = StandardCharsets.UTF_8;

    public KryoRedisCodec(KryoPoolHolder kryoPoolHolder) {
        this.kryoPoolHolder = kryoPoolHolder;
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        if (key == null) {
            return ByteBuffer.wrap(new byte[0]);
        }
        return ByteBuffer.wrap(key.getBytes(charset));
    }

    @Override
    public String decodeKey(ByteBuffer bytes) {
        if (bytes == null) {
            return null;
        }
        return charset.decode(bytes).toString();
    }

    @Override
    public ByteBuffer encodeValue(Object value) {
        if (value == null) {
            return ByteBuffer.wrap(new byte[0]);
        }
        byte[] serialized = kryoPoolHolder.serialize(value);
        return ByteBuffer.wrap(serialized);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        if (bytes == null || !bytes.hasRemaining()) {
            return null;
        }
        byte[] array = new byte[bytes.remaining()];
        bytes.get(array);
        return kryoPoolHolder.deserialize(array);
    }
}
