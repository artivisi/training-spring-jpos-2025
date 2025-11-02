package com.artivisi.atm.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for AES-CMAC and HMAC-SHA256 MAC generation utilities.
 */
class AesCmacUtilTest {

    private static final byte[] TEST_TMK_AES128 = {
            0x2b, 0x7e, 0x15, 0x16, 0x28, (byte) 0xae, (byte) 0xd2, (byte) 0xa6,
            (byte) 0xab, (byte) 0xf7, 0x15, (byte) 0x88, 0x09, (byte) 0xcf, 0x4f, 0x3c
    }; // 16-byte AES-128 key

    private static final byte[] TEST_DATA = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    @Test
    void testGenerateMac_AesCmac() {
        byte[] mac = AesCmacUtil.generateMac(TEST_DATA, TEST_TMK_AES128);

        assertNotNull(mac);
        assertEquals(16, mac.length, "AES-CMAC should produce 16-byte MAC");
    }

    @Test
    void testVerifyMac_AesCmac_Valid() {
        byte[] mac = AesCmacUtil.generateMac(TEST_DATA, TEST_TMK_AES128);

        boolean isValid = AesCmacUtil.verifyMac(TEST_DATA, mac, TEST_TMK_AES128);

        assertTrue(isValid, "MAC verification should succeed for valid MAC");
    }

    @Test
    void testVerifyMac_AesCmac_Invalid() {
        byte[] mac = AesCmacUtil.generateMac(TEST_DATA, TEST_TMK_AES128);

        // Corrupt the MAC
        mac[0] = (byte) ~mac[0];

        boolean isValid = AesCmacUtil.verifyMac(TEST_DATA, mac, TEST_TMK_AES128);

        assertFalse(isValid, "MAC verification should fail for corrupted MAC");
    }

    @Test
    void testGenerateMac_AesCmac_Deterministic() {
        byte[] mac1 = AesCmacUtil.generateMac(TEST_DATA, TEST_TMK_AES128);
        byte[] mac2 = AesCmacUtil.generateMac(TEST_DATA, TEST_TMK_AES128);

        assertArrayEquals(mac1, mac2, "Same data and key should produce same MAC");
    }

    @Test
    void testGenerateMac_HmacSha256Truncated() {
        byte[] mac = AesCmacUtil.generateHmacSha256Truncated(TEST_DATA, TEST_TMK_AES128);

        assertNotNull(mac);
        assertEquals(16, mac.length, "Truncated HMAC-SHA256 should be 16 bytes");
    }

    @Test
    void testVerifyMac_HmacSha256_Valid() {
        byte[] mac = AesCmacUtil.generateHmacSha256Truncated(TEST_DATA, TEST_TMK_AES128);

        boolean isValid = AesCmacUtil.verifyHmacSha256Truncated(TEST_DATA, mac, TEST_TMK_AES128);

        assertTrue(isValid, "HMAC-SHA256 verification should succeed for valid MAC");
    }

    @Test
    void testVerifyMac_HmacSha256_Invalid() {
        byte[] mac = AesCmacUtil.generateHmacSha256Truncated(TEST_DATA, TEST_TMK_AES128);

        // Corrupt the MAC
        mac[5] = (byte) ~mac[5];

        boolean isValid = AesCmacUtil.verifyHmacSha256Truncated(TEST_DATA, mac, TEST_TMK_AES128);

        assertFalse(isValid, "HMAC-SHA256 verification should fail for corrupted MAC");
    }

    @Test
    void testGenerateMac_EmptyData() {
        byte[] emptyData = new byte[0];

        assertThrows(IllegalArgumentException.class, () ->
                AesCmacUtil.generateMac(emptyData, TEST_TMK_AES128)
        );
    }

    @Test
    void testGenerateMac_InvalidKeySize() {
        byte[] invalidKey = new byte[15]; // Not 16 or 32 bytes

        assertThrows(IllegalArgumentException.class, () ->
                AesCmacUtil.generateMac(TEST_DATA, invalidKey)
        );
    }

    @Test
    void testGenerateMac_Aes256Key() {
        byte[] aes256Key = new byte[32]; // 32-byte AES-256 key
        for (int i = 0; i < 32; i++) {
            aes256Key[i] = (byte) i;
        }

        byte[] mac = AesCmacUtil.generateMac(TEST_DATA, aes256Key);

        assertNotNull(mac);
        assertEquals(16, mac.length, "AES-CMAC should produce 16-byte MAC even with AES-256");
    }

    @Test
    void testMacDiffers_WithDifferentKeys() {
        byte[] key1 = new byte[16];
        byte[] key2 = new byte[16];
        for (int i = 0; i < 16; i++) {
            key1[i] = (byte) i;
            key2[i] = (byte) (i + 1);
        }

        byte[] mac1 = AesCmacUtil.generateMac(TEST_DATA, key1);
        byte[] mac2 = AesCmacUtil.generateMac(TEST_DATA, key2);

        assertFalse(java.util.Arrays.equals(mac1, mac2), "Different keys should produce different MACs");
    }

    @Test
    void testMacDiffers_WithDifferentData() {
        byte[] data1 = "Hello".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "World".getBytes(StandardCharsets.UTF_8);

        byte[] mac1 = AesCmacUtil.generateMac(data1, TEST_TMK_AES128);
        byte[] mac2 = AesCmacUtil.generateMac(data2, TEST_TMK_AES128);

        assertFalse(java.util.Arrays.equals(mac1, mac2), "Different data should produce different MACs");
    }
}
