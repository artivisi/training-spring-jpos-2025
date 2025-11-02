package com.artivisi.atm.util;

import com.artivisi.atm.dto.hsm.PinFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for AES-128 PIN block encryption/decryption utilities.
 */
class AesPinBlockUtilTest {

    private static final byte[] TEST_TPK_AES128 = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
    }; // 16-byte AES-128 key

    private static final String BANK_UUID = "48a9e84c-ff57-4483-bf83-b255f34a6466";

    @Test
    void testEncryptDecryptPinBlock_Iso0() {
        String pin = "1234";
        String pan = "4111111111111111";
        

        // Build clear PIN block
        byte[] clearPinBlock = AesPinBlockUtil.buildClearPinBlock(pin, pan, PinFormat.ISO_0);
        assertEquals(16, clearPinBlock.length, "Clear PIN block should be 16 bytes");

        // Encrypt
        byte[] encrypted = AesPinBlockUtil.encryptPinBlock(clearPinBlock, TEST_TPK_AES128, BANK_UUID);
        assertEquals(16, encrypted.length, "Encrypted PIN block should be 16 bytes");

        // Decrypt
        byte[] decrypted = AesPinBlockUtil.decryptPinBlock(encrypted, TEST_TPK_AES128, BANK_UUID);
        assertArrayEquals(clearPinBlock, decrypted, "Decrypted PIN block should match original");

        // Extract PIN
        String extractedPin = AesPinBlockUtil.extractPinFromIso0Block(decrypted, pan);
        assertEquals(pin, extractedPin, "Extracted PIN should match original");
    }

    @Test
    void testBuildClearPinBlock_Iso0() {
        String pin = "1234";
        String pan = "4111111111111111";

        byte[] pinBlock = AesPinBlockUtil.buildClearPinBlock(pin, pan, PinFormat.ISO_0);

        assertEquals(16, pinBlock.length);
        // First byte should be 0x04 (format 0, length 4)
        assertEquals(0x04, pinBlock[0] & 0xFF);
    }

    @Test
    void testBuildClearPinBlock_Iso1() {
        String pin = "1234";
        String pan = "4111111111111111";

        byte[] pinBlock = AesPinBlockUtil.buildClearPinBlock(pin, pan, PinFormat.ISO_1);

        assertEquals(16, pinBlock.length);
        // First byte should be 0x14 (format 1, length 4)
        assertEquals(0x14, pinBlock[0] & 0xFF);
    }

    @Test
    void testEncryptPinBlock_InvalidKeySize() {
        byte[] clearPinBlock = new byte[16];
        byte[] invalidKey = new byte[15]; // Wrong size

        assertThrows(IllegalArgumentException.class, () ->
                AesPinBlockUtil.encryptPinBlock(clearPinBlock, invalidKey, BANK_UUID)
        );
    }

    @Test
    void testEncryptPinBlock_InvalidBlockSize() {
        byte[] invalidBlock = new byte[8]; // Wrong size
        byte[] validKey = new byte[16];

        assertThrows(IllegalArgumentException.class, () ->
                AesPinBlockUtil.encryptPinBlock(invalidBlock, validKey, BANK_UUID)
        );
    }

    @Test
    void testExtractPinFromIso0Block() {
        String pin = "123456";
        String pan = "5432100012345678";

        byte[] pinBlock = AesPinBlockUtil.buildClearPinBlock(pin, pan, PinFormat.ISO_0);
        String extractedPin = AesPinBlockUtil.extractPinFromIso0Block(pinBlock, pan);

        assertEquals(pin, extractedPin);
    }

    @Test
    void testRoundTrip_DifferentPinLengths() {
        String[] testPins = {"1234", "12345", "123456", "1234567", "12345678"};
        String pan = "4111111111111111";

        for (String pin : testPins) {
            byte[] clearPinBlock = AesPinBlockUtil.buildClearPinBlock(pin, pan, PinFormat.ISO_0);
            byte[] encrypted = AesPinBlockUtil.encryptPinBlock(clearPinBlock, TEST_TPK_AES128, BANK_UUID);
            byte[] decrypted = AesPinBlockUtil.decryptPinBlock(encrypted, TEST_TPK_AES128, BANK_UUID);
            String extractedPin = AesPinBlockUtil.extractPinFromIso0Block(decrypted, pan);

            assertEquals(pin, extractedPin, "PIN mismatch for length " + pin.length());
        }
    }
}
