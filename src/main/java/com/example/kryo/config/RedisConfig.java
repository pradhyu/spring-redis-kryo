package com.example.kryo.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
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

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * 1. Standalone Lettuce RedisClient
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient lettuceRedisClient() {
        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(Duration.ofSeconds(3))
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
}
