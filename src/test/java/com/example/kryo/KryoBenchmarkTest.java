package com.example.kryo;

import com.example.kryo.config.KryoPoolHolder;
import com.example.kryo.model.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KryoBenchmarkTest {

    private KryoPoolHolder kryoPoolHolder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kryoPoolHolder = new KryoPoolHolder(64, 16384, 1);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private List<UserProfile> generateUsers(int count) {
        List<UserProfile> list = new ArrayList<>(count);
        Instant now = Instant.now();
        for (int i = 1; i <= count; i++) {
            list.add(new UserProfile(
                    (long) i,
                    "user_" + i,
                    "user" + i + "@company.internal",
                    i % 2 == 0,
                    List.of("ROLE_USER", "ROLE_ANALYST"),
                    Map.of("theme", "dark", "locale", "en_US", "notifications", "email"),
                    now
            ));
        }
        return list;
    }

    @Test
    @DisplayName("Benchmark: 10,000 Users Serialization / Deserialization (Kryo vs JSON vs Java Native)")
    void benchmarkTenThousandUsers() throws Exception {
        int userCount = 10_000;
        System.out.println("\n==========================================================================");
        System.out.println(">>> BENCHMARK: 10,000 UserProfiles (Kryo vs Jackson JSON vs Java Native) <<<");
        System.out.println("==========================================================================");

        List<UserProfile> users = generateUsers(userCount);

        // 1. Warm-up (JVM JIT compilation)
        for (int w = 0; w < 5; w++) {
            // Kryo warm-up
            byte[] wKryo = kryoPoolHolder.serialize(users);
            kryoPoolHolder.deserialize(wKryo);
            // JSON warm-up
            byte[] wJson = objectMapper.writeValueAsBytes(users);
            objectMapper.readValue(wJson, new TypeReference<List<UserProfile>>() {});
            // Java native warm-up
            java.io.ByteArrayOutputStream javaWarmBaos = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(javaWarmBaos)) {
                oos.writeObject(users);
            }
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(javaWarmBaos.toByteArray()))) {
                ois.readObject();
            }
        }

        // 2. Benchmark Kryo Serialization
        long startKryoSer = System.nanoTime();
        byte[] kryoBytes = kryoPoolHolder.serialize(users);
        long durationKryoSerNs = System.nanoTime() - startKryoSer;
        double durationKryoSerMs = durationKryoSerNs / 1_000_000.0;

        // 3. Benchmark Kryo Deserialization
        long startKryoDeser = System.nanoTime();
        List<UserProfile> deserializedKryo = kryoPoolHolder.deserialize(kryoBytes, ArrayList.class);
        long durationKryoDeserNs = System.nanoTime() - startKryoDeser;
        double durationKryoDeserMs = durationKryoDeserNs / 1_000_000.0;

        assertNotNull(deserializedKryo);
        assertEquals(userCount, deserializedKryo.size());

        // 4. Benchmark Jackson JSON Serialization
        long startJsonSer = System.nanoTime();
        byte[] jsonBytes = objectMapper.writeValueAsBytes(users);
        long durationJsonSerNs = System.nanoTime() - startJsonSer;
        double durationJsonSerMs = durationJsonSerNs / 1_000_000.0;

        // 5. Benchmark Jackson JSON Deserialization
        long startJsonDeser = System.nanoTime();
        List<UserProfile> deserializedJson = objectMapper.readValue(jsonBytes, new TypeReference<List<UserProfile>>() {});
        long durationJsonDeserNs = System.nanoTime() - startJsonDeser;
        double durationJsonDeserMs = durationJsonDeserNs / 1_000_000.0;

        assertNotNull(deserializedJson);
        assertEquals(userCount, deserializedJson.size());

        // 6. Benchmark Java Native Serialization (ObjectOutputStream)
        long startJavaSer = System.nanoTime();
        java.io.ByteArrayOutputStream javaBaos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(javaBaos)) {
            oos.writeObject(users);
        }
        byte[] javaBytes = javaBaos.toByteArray();
        long durationJavaSerNs = System.nanoTime() - startJavaSer;
        double durationJavaSerMs = durationJavaSerNs / 1_000_000.0;

        // 7. Benchmark Java Native Deserialization (ObjectInputStream)
        long startJavaDeser = System.nanoTime();
        List<?> deserializedJava;
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(javaBytes))) {
            deserializedJava = (List<?>) ois.readObject();
        }
        long durationJavaDeserNs = System.nanoTime() - startJavaDeser;
        double durationJavaDeserMs = durationJavaDeserNs / 1_000_000.0;

        assertNotNull(deserializedJava);
        assertEquals(userCount, deserializedJava.size());

        // 8. Report metrics & comparisons
        double vsJsonSizeSavings = (1.0 - ((double) kryoBytes.length / jsonBytes.length)) * 100.0;
        double vsJavaSizeSavings = (1.0 - ((double) kryoBytes.length / javaBytes.length)) * 100.0;

        System.out.printf("Dataset: %,d UserProfile objects\n", userCount);
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf("%-18s | %-16s | %-16s | %-16s\n", "Metric", "Kryo 5 (Binary)", "Jackson JSON", "Java Native");
        System.out.println("-------------------+------------------+------------------+----------------");
        System.out.printf("%-18s | %,9d bytes  | %,9d bytes  | %,9d bytes \n", "Payload Size", kryoBytes.length, jsonBytes.length, javaBytes.length);
        System.out.printf("%-18s | %9.2f KB       | %9.2f KB       | %9.2f KB      \n", "Payload KB", kryoBytes.length / 1024.0, jsonBytes.length / 1024.0, javaBytes.length / 1024.0);
        System.out.printf("%-18s | %9.2f MB       | %9.2f MB       | %9.2f MB      \n", "Payload MB", kryoBytes.length / (1024.0 * 1024.0), jsonBytes.length / (1024.0 * 1024.0), javaBytes.length / (1024.0 * 1024.0));
        System.out.printf("%-18s | %9.2f ms       | %9.2f ms       | %9.2f ms      \n", "Serialization Time", durationKryoSerMs, durationJsonSerMs, durationJavaSerMs);
        System.out.printf("%-18s | %,9.0f ops/s   | %,9.0f ops/s   | %,9.0f ops/s  \n", "Ser Throughput", (userCount / (durationKryoSerMs / 1000.0)), (userCount / (durationJsonSerMs / 1000.0)), (userCount / (durationJavaSerMs / 1000.0)));
        System.out.printf("%-18s | %9.2f ms       | %9.2f ms       | %9.2f ms      \n", "Deserialization", durationKryoDeserMs, durationJsonDeserMs, durationJavaDeserMs);
        System.out.printf("%-18s | %,9.0f ops/s   | %,9.0f ops/s   | %,9.0f ops/s  \n", "Deser Throughput", (userCount / (durationKryoDeserMs / 1000.0)), (userCount / (durationJsonDeserMs / 1000.0)), (userCount / (durationJavaDeserMs / 1000.0)));
        System.out.println("-------------------+------------------+------------------+----------------");
        System.out.printf("Size Comparison    : Kryo is %.1f%% smaller than JSON, and %.1f%% smaller than Java Native\n", vsJsonSizeSavings, vsJavaSizeSavings);
        System.out.printf("Ser Speedup Factor : Kryo is %.2fx faster than JSON, and %.2fx faster than Java Native\n", (durationJsonSerMs / durationKryoSerMs), (durationJavaSerMs / durationKryoSerMs));
        System.out.printf("Deser Speedup      : Kryo is %.2fx faster than JSON, and %.2fx faster than Java Native\n", (durationJsonDeserMs / durationKryoDeserMs), (durationJavaDeserMs / durationKryoDeserMs));
        System.out.println("==========================================================================\n");

        assertTrue(kryoBytes.length < jsonBytes.length, "Kryo payload must be smaller than JSON");
        assertTrue(kryoBytes.length < javaBytes.length, "Kryo payload must be smaller than Java Native");
    }
}
