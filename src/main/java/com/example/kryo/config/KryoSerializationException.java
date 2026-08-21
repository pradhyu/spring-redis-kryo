package com.example.kryo.config;

/**
 * Custom exception for Kryo serialization/deserialization failures.
 * Allows callers to distinguish Kryo-specific failures from other runtime errors.
 */
public class KryoSerializationException extends RuntimeException {

    public KryoSerializationException(String message) {
        super(message);
    }

    public KryoSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Thrown when the schema version byte in a deserialized payload
     * does not match the expected version.
     */
    public static class SchemaVersionMismatchException extends KryoSerializationException {
        private final byte expected;
        private final byte actual;

        public SchemaVersionMismatchException(byte expected, byte actual) {
            super("Kryo schema version mismatch: expected " + expected + " but got " + actual +
                  ". Data was serialized with an incompatible registration map.");
            this.expected = expected;
            this.actual = actual;
        }

        public byte getExpected() { return expected; }
        public byte getActual() { return actual; }
    }
}
