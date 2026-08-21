package com.example.kryo.service;

import com.example.kryo.config.KryoPoolHolder;
import com.example.kryo.model.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmark service comparing Kryo, Jackson JSON, and Java Native serialization.
 * Extracted from DemoController for separation of concerns.
 */
@Service
public class BenchmarkService {

    private final KryoPoolHolder kryoPoolHolder;
    private final LettuceKryoDirectService lettuceKryoService;
    private final ObjectMapper objectMapper;

    public BenchmarkService(KryoPoolHolder kryoPoolHolder, LettuceKryoDirectService lettuceKryoService) {
        this.kryoPoolHolder = kryoPoolHolder;
        this.lettuceKryoService = lettuceKryoService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Generate test UserProfile objects.
     */
    public List<UserProfile> generateUsers(int count) {
        List<UserProfile> users = new ArrayList<>(count);
        Instant now = Instant.now();
        for (int i = 1; i <= count; i++) {
            users.add(new UserProfile(
                    (long) i,
                    "user_" + i,
                    "user" + i + "@enterprise.io",
                    i % 2 == 0,
                    List.of("ROLE_USER", "ROLE_ENGINEER"),
                    Map.of("theme", "dark", "locale", "en_US", "notifications", "email"),
                    now
            ));
        }
        return users;
    }

    /**
     * Run the full benchmark suite: Kryo vs JSON vs Java Native serialization,
     * plus Redis round-trip timing.
     */
    public Map<String, Object> runBenchmark(int count) {
        List<UserProfile> users = generateUsers(count);

        // 1. Kryo Serialization
        long startKryoSer = System.nanoTime();
        byte[] kryoBytes = kryoPoolHolder.serialize(users);
        double durationKryoSerMs = (System.nanoTime() - startKryoSer) / 1_000_000.0;

        // 2. Kryo Deserialization
        long startKryoDeser = System.nanoTime();
        kryoPoolHolder.deserialize(kryoBytes, ArrayList.class);
        double durationKryoDeserMs = (System.nanoTime() - startKryoDeser) / 1_000_000.0;

        // 3. Jackson JSON Serialization & Deserialization
        byte[] jsonBytes = new byte[0];
        double durationJsonSerMs = 0;
        double durationJsonDeserMs = 0;
        try {
            long startJsonSer = System.nanoTime();
            jsonBytes = objectMapper.writeValueAsBytes(users);
            durationJsonSerMs = (System.nanoTime() - startJsonSer) / 1_000_000.0;

            long startJsonDeser = System.nanoTime();
            objectMapper.readValue(jsonBytes, new TypeReference<List<UserProfile>>() {});
            durationJsonDeserMs = (System.nanoTime() - startJsonDeser) / 1_000_000.0;
        } catch (Exception ignored) {
        }

        // 4. Java Native Serialization (ObjectOutputStream / ObjectInputStream)
        byte[] javaBytes = new byte[0];
        double durationJavaSerMs = 0;
        double durationJavaDeserMs = 0;
        try {
            long startJavaSer = System.nanoTime();
            ByteArrayOutputStream javaBaos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(javaBaos)) {
                oos.writeObject(users);
            }
            javaBytes = javaBaos.toByteArray();
            durationJavaSerMs = (System.nanoTime() - startJavaSer) / 1_000_000.0;

            long startJavaDeser = System.nanoTime();
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(javaBytes))) {
                ois.readObject();
            }
            durationJavaDeserMs = (System.nanoTime() - startJavaDeser) / 1_000_000.0;
        } catch (Exception ignored) {
        }

        // 5. Redis round-trip via Lettuce Kryo
        String redisKey = "benchmark:users";
        long startRedisSet = System.nanoTime();
        lettuceKryoService.set(redisKey, users);
        double redisSetMs = (System.nanoTime() - startRedisSet) / 1_000_000.0;

        long startRedisGet = System.nanoTime();
        List<?> retrievedUsers = (List<?>) lettuceKryoService.get(redisKey);
        double redisGetMs = (System.nanoTime() - startRedisGet) / 1_000_000.0;

        // 6. Calculate comparisons
        double vsJsonSizeSavings = jsonBytes.length > 0 ? (1.0 - ((double) kryoBytes.length / jsonBytes.length)) * 100.0 : 0;
        double vsJavaSizeSavings = javaBytes.length > 0 ? (1.0 - ((double) kryoBytes.length / javaBytes.length)) * 100.0 : 0;

        // Build result map
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("dataset", Map.of(
                "objectType", "UserProfile",
                "totalCount", count,
                "redisKey", redisKey
        ));
        result.put("kryo", Map.of(
                "payloadBytes", kryoBytes.length,
                "payloadKB", String.format("%.2f KB", kryoBytes.length / 1024.0),
                "payloadMB", String.format("%.2f MB", kryoBytes.length / (1024.0 * 1024.0)),
                "serializeTimeMs", Double.parseDouble(String.format("%.2f", durationKryoSerMs)),
                "deserializeTimeMs", Double.parseDouble(String.format("%.2f", durationKryoDeserMs)),
                "serializeThroughputOpsPerSec", (long) (count / (durationKryoSerMs / 1000.0)),
                "deserializeThroughputOpsPerSec", (long) (count / (durationKryoDeserMs / 1000.0))
        ));
        result.put("jsonComparison", Map.of(
                "payloadBytes", jsonBytes.length,
                "payloadKB", String.format("%.2f KB", jsonBytes.length / 1024.0),
                "payloadMB", String.format("%.2f MB", jsonBytes.length / (1024.0 * 1024.0)),
                "serializeTimeMs", Double.parseDouble(String.format("%.2f", durationJsonSerMs)),
                "deserializeTimeMs", Double.parseDouble(String.format("%.2f", durationJsonDeserMs)),
                "sizeReductionPercentage", String.format("%.1f%% smaller with Kryo", vsJsonSizeSavings),
                "serializationSpeedup", String.format("%.2fx faster with Kryo", durationJsonSerMs / durationKryoSerMs),
                "deserializationSpeedup", String.format("%.2fx faster with Kryo", durationJsonDeserMs / durationKryoDeserMs)
        ));
        result.put("javaNativeComparison", Map.of(
                "payloadBytes", javaBytes.length,
                "payloadKB", String.format("%.2f KB", javaBytes.length / 1024.0),
                "payloadMB", String.format("%.2f MB", javaBytes.length / (1024.0 * 1024.0)),
                "serializeTimeMs", Double.parseDouble(String.format("%.2f", durationJavaSerMs)),
                "deserializeTimeMs", Double.parseDouble(String.format("%.2f", durationJavaDeserMs)),
                "sizeReductionPercentage", String.format("%.1f%% smaller with Kryo", vsJavaSizeSavings),
                "serializationSpeedup", String.format("%.2fx faster with Kryo", durationJavaSerMs / durationKryoSerMs),
                "deserializationSpeedup", String.format("%.2fx faster with Kryo", durationJavaDeserMs / durationKryoDeserMs)
        ));
        result.put("redisOperations", Map.of(
                "key", redisKey,
                "redisSetTimeMs", Double.parseDouble(String.format("%.2f", redisSetMs)),
                "redisGetTimeMs", Double.parseDouble(String.format("%.2f", redisGetMs)),
                "retrievedUsersCount", retrievedUsers != null ? retrievedUsers.size() : 0,
                "roundTripVerified", retrievedUsers != null && retrievedUsers.size() == count
        ));
        return result;
    }
}
