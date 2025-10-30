package com.example.atm.entity;

/**
 * PIN encryption algorithm types supported by the system.
 */
public enum PinEncryptionAlgorithm {
    /**
     * Triple DES (3DES) encryption - legacy standard.
     * Produces 8-byte encrypted PIN blocks.
     * Uses field 52 in ISO-8583 messages.
     */
    TDES("3DES", 8, 52),

    /**
     * AES-128 encryption - modern standard.
     * Produces 16-byte encrypted PIN blocks.
     * Uses field 123 in ISO-8583 messages (private use field).
     */
    AES_128("AES-128", 16, 123);

    private final String displayName;
    private final int blockSize;
    private final int isoField;

    PinEncryptionAlgorithm(String displayName, int blockSize, int isoField) {
        this.displayName = displayName;
        this.blockSize = blockSize;
        this.isoField = isoField;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the encrypted PIN block size in bytes.
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Get the ISO-8583 field number for this encryption algorithm.
     */
    public int getIsoField() {
        return isoField;
    }

    /**
     * Get the JCE cipher transformation string.
     */
    public String getCipherTransformation() {
        return switch (this) {
            case TDES -> "DESede/ECB/NoPadding";
            case AES_128 -> "AES/ECB/NoPadding";
        };
    }

    /**
     * Get the key algorithm name for JCE.
     */
    public String getKeyAlgorithm() {
        return switch (this) {
            case TDES -> "DESede";
            case AES_128 -> "AES";
        };
    }

    /**
     * Get expected key length in bytes.
     */
    public int getKeyLength() {
        return switch (this) {
            case TDES -> 24;  // 192 bits for 3DES (3 keys)
            case AES_128 -> 16;  // 128 bits
        };
    }
}
