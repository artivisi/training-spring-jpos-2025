package com.artivisi.atm.entity;

/**
 * PIN encryption algorithm types supported by the system.
 * Only AES-128 and AES-256 are supported.
 */
public enum PinEncryptionAlgorithm {
    /**
     * AES-128 encryption.
     * Produces 32-byte encrypted PIN blocks (16-byte IV + 16-byte ciphertext).
     * Uses field 123 in ISO-8583 messages (private use field).
     */
    AES_128("AES-128", 32, 123),

    /**
     * AES-256 encryption - enhanced security (recommended).
     * Produces 32-byte encrypted PIN blocks (16-byte IV + 16-byte ciphertext).
     * Uses field 123 in ISO-8583 messages (private use field).
     */
    AES_256("AES-256", 32, 123);

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
     * Get the encrypted PIN block size in bytes (includes IV + ciphertext).
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
        return "AES/CBC/PKCS5Padding";
    }

    /**
     * Get the key algorithm name for JCE.
     */
    public String getKeyAlgorithm() {
        return "AES";
    }

    /**
     * Get expected key length in bytes.
     */
    public int getKeyLength() {
        return switch (this) {
            case AES_128 -> 16;  // 128 bits
            case AES_256 -> 32;  // 256 bits
        };
    }
}
