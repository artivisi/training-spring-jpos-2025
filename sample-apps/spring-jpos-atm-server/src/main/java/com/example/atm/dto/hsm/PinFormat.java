package com.example.atm.dto.hsm;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * PIN block format according to ISO 9564.
 */
public enum PinFormat {
    /**
     * ISO Format 0 (ANSI X9.8): PIN + padding + XOR with PAN
     * Most commonly used format
     */
    ISO_0("ISO-0"),

    /**
     * ISO Format 1: PIN + random padding
     */
    ISO_1("ISO-1"),

    /**
     * ISO Format 3: PIN + random digits
     */
    ISO_3("ISO-3"),

    /**
     * ISO Format 4: PIN + random hex
     */
    ISO_4("ISO-4");

    private final String value;

    PinFormat(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static PinFormat fromValue(String value) {
        for (PinFormat format : values()) {
            if (format.value.equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown PIN format: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
