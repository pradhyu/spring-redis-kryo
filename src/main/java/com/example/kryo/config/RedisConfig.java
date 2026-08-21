package com.example.kryo.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Direct Lettuce Redis Configuration using KryoRedisCodec.
 * Plugs Kryo binary serialization directly into Lettuce's Netty pipeline.
 */
@Configuration
public class RedisConfig {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.timeout:3s}")
    private Duration redisTimeout;

    /**
     * 1. Standalone Lettuce RedisClient
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient lettuceRedisClient() {
        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(redisTimeout)
                .build();
        return RedisClient.create(redisURI);
    }

    /**
     * 2. Direct Stateful Lettuce Redis Connection backed by KryoRedisCodec
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, Object> lettuceKryoConnection(RedisClient redisClient,
                                                                         KryoRedisCodec kryoRedisCodec) {
        return redisClient.connect(kryoRedisCodec);
    }

    /**
     * 3. Dedicated raw byte connection for binary inspection (reused, not per-call).
     * Fixes the TCP connection leak where a new connection was opened on every getRawBytes() call.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> rawByteConnection(RedisClient redisClient) {
        return redisClient.connect(ByteArrayCodec.INSTANCE);
    }
}
