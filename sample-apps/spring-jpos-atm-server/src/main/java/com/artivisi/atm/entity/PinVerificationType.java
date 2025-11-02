package com.artivisi.atm.entity;

/**
 * Types of PIN verification methods supported by the system.
 *
 * ENCRYPTED_PIN_BLOCK: Traditional translation method where both TPK-encrypted and LMK-encrypted
 *                       PIN blocks are compared by HSM
 * PVV: PIN Verification Value method using one-way hash (ISO 9564 compliant)
 */
public enum PinVerificationType {
    /**
     * Encrypted PIN block verification (Translation method).
     * Compares TPK-encrypted PIN from terminal with LMK-encrypted PIN from database.
     */
    ENCRYPTED_PIN_BLOCK,

    /**
     * PIN Verification Value method.
     * Uses 4-digit PVV generated from PIN using one-way hash.
     */
    PVV
}
