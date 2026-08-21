package com.example.kryo.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * 1. Lettuce Connection Factory for Spring Data Redis
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    /**
     * 2. Spring Data RedisTemplate configured with Kryo serializer
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       KryoPoolHolder kryoPoolHolder) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        KryoRedisSerializer<Object> kryoSerializer = new KryoRedisSerializer<>(kryoPoolHolder);

        // Key serializers (UTF-8 String)
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value serializers (Kryo binary)
        template.setValueSerializer(kryoSerializer);
        template.setHashValueSerializer(kryoSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 3. Standalone Lettuce RedisClient for direct Lettuce operations
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
     * 4. Stateful Lettuce Redis connection using Kryo RedisCodec
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, Object> lettuceKryoConnection(RedisClient redisClient,
                                                                         KryoRedisCodec kryoRedisCodec) {
        return redisClient.connect(kryoRedisCodec);
    }
}
