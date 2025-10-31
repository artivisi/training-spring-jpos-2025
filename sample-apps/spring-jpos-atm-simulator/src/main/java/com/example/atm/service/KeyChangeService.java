package com.example.atm.service;

import com.example.atm.domain.model.CryptoKey;
import com.example.atm.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.MUX;
import org.jpos.iso.packager.BASE24Packager;
import org.jpos.util.NameRegistrar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service for handling terminal-initiated key change operations via ISO-8583.
 * Follows standard banking key change protocol:
 * 1. ATM sends key change request (MTI 0800) to server
 * 2. Server coordinates with HSM to generate new key
 * 3. Server encrypts new key under TMK and sends response (MTI 0810)
 * 4. ATM decrypts new key, verifies KCV, and activates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyChangeService {

    private final CryptoKeyService cryptoKeyService;
    private final RuntimeKeyManager runtimeKeyManager;
    private final String muxName;

    @Value("${terminal.id}")
    private String terminalId;

    @Value("${terminal.institution.id}")
    private String institutionId;

    private final BASE24Packager packager = new BASE24Packager();

    /**
     * Initiate key change request to server via ISO-8583.
     *
     * @param keyType Type of key to rotate (TPK or TSK)
     * @return New key after successful rotation
     */
    @Transactional
    public CryptoKey initiateKeyChange(CryptoKey.KeyType keyType) throws Exception {
        log.info("Initiating key change for key type: {}", keyType);

        // Build ISO-8583 0800 key change request
        ISOMsg request = buildKeyChangeRequest(keyType);

        // Send request via MUX
        log.info("Sending key change request via MUX: {}", muxName);
        MUX mux = (MUX) NameRegistrar.get(muxName);

        if (!mux.isConnected()) {
            throw new RuntimeException("MUX not connected");
        }

        ISOMsg response = mux.request(request, 30000);

        if (response == null) {
            throw new RuntimeException("No response from server for key change request");
        }

        // Parse and process response
        return processKeyChangeResponse(response, keyType);
    }

    /**
     * Build ISO-8583 0800 (Network Management Request) message for key change.
     * Per KEY_CHANGE_PROTOCOL.md:
     * - MTI: 0800
     * - Field 11: STAN (6 digits)
     * - Field 41: Terminal ID (16 chars, left-padded with spaces)
     * - Field 53: Key type (16 digits: "0100000000000000" for TPK, "0200000000000000" for TSK)
     * - Field 64: MAC
     */
    private ISOMsg buildKeyChangeRequest(CryptoKey.KeyType keyType) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI("0800");

        // Field 11: STAN
        msg.set(11, String.format("%06d", System.currentTimeMillis() % 1000000));

        // Field 41: Terminal ID (left-padded to 16 characters)
        String fullTerminalId = institutionId + "-" + terminalId; // e.g., "TRM-ISS001-ATM-001"
        msg.set(41, String.format("%-16s", fullTerminalId));

        // Field 53: Security Control Information (16 digits)
        // First 2 digits: key type (01=TPK, 02=TSK)
        // Remaining 14 digits: reserved (00000000000000)
        String keyTypeCode = getKeyTypeCode(keyType);
        msg.set(53, keyTypeCode + "00000000000000");

        log.info("Built key change request:");
        log.info("  MTI: {}", msg.getMTI());
        log.info("  Field 11 (STAN): {}", msg.getString(11));
        log.info("  Field 41 (Terminal ID): [{}]", msg.getString(41));
        log.info("  Field 53 (Key Type): {}", msg.getString(53));

        return msg;
    }

    /**
     * Process key change response (MTI 0810).
     * Per KEY_CHANGE_PROTOCOL.md:
     * - Field 39: Response Code (00 = success, 30 = format error, 96 = system error)
     * - Field 48: SHA-256 checksum (16 hex chars)
     * - Field 53: Echoed from request
     * - Field 64: MAC
     * - Field 123: Encrypted new key (96 hex chars = 32 IV + 64 ciphertext)
     */
    private CryptoKey processKeyChangeResponse(ISOMsg response, CryptoKey.KeyType keyType) throws Exception {
        String mti = response.getMTI();
        if (!"0810".equals(mti)) {
            throw new RuntimeException("Invalid response MTI: " + mti + ", expected 0810");
        }

        String responseCode = response.getString(39);
        if (!"00".equals(responseCode)) {
            String errorMsg = "Key change failed with response code: " + responseCode;
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        log.info("Key change approved by server, processing encrypted key");

        // Field 123: Encrypted key (96 hex chars = 32 IV + 64 ciphertext)
        String encryptedKeyHex = response.getString(123);
        if (encryptedKeyHex == null || encryptedKeyHex.isEmpty()) {
            throw new RuntimeException("No encrypted key in response field 123");
        }

        if (encryptedKeyHex.length() != 96) {
            throw new RuntimeException("Invalid encrypted key length in field 123: " +
                                     encryptedKeyHex.length() + ", expected 96");
        }

        // Field 48: SHA-256 checksum (16 hex chars)
        String expectedChecksum = response.getString(48);
        if (expectedChecksum == null || expectedChecksum.isEmpty()) {
            throw new RuntimeException("No checksum in response field 48");
        }

        if (expectedChecksum.length() != 16) {
            throw new RuntimeException("Invalid checksum length in field 48: " +
                                     expectedChecksum.length() + ", expected 16");
        }

        log.debug("Encrypted key (field 123): {} chars", encryptedKeyHex.length());
        log.debug("Expected checksum (field 48): {}", expectedChecksum);

        // Decrypt key using current active key (TPK or TSK)
        byte[] newKeyBytes = decryptNewKey(encryptedKeyHex, keyType);
        String newKeyHex = CryptoUtil.bytesToHex(newKeyBytes);

        log.info("Decrypted new key: {} bytes", newKeyBytes.length);

        // Verify SHA-256 checksum
        String calculatedChecksum = calculateChecksum(newKeyBytes);
        if (!calculatedChecksum.equalsIgnoreCase(expectedChecksum)) {
            log.error("Checksum mismatch! Expected: {}, Calculated: {}",
                     expectedChecksum, calculatedChecksum);
            throw new RuntimeException("SHA-256 checksum verification failed");
        }

        log.info("Checksum verification successful: {}", calculatedChecksum);

        // Store and activate new key
        CryptoKey newKey = cryptoKeyService.rotateKey(keyType, newKeyHex, calculatedChecksum);

        // Reload key into runtime memory
        runtimeKeyManager.reloadKey(keyType);

        log.info("Key change completed successfully for {}: keyId={}, checksum={}",
                keyType, newKey.getId(), calculatedChecksum);

        return newKey;
    }

    /**
     * Decrypt new key from field 123 using current active key.
     * Per KEY_CHANGE_PROTOCOL.md:
     * - Field 123 format: [IV (32 hex)] || [Ciphertext (64 hex)]
     * - Algorithm: AES-128-CBC with PKCS5Padding
     * - Decryption key: Current active master key (first 16 bytes = AES-128)
     */
    private byte[] decryptNewKey(String encryptedKeyHex, CryptoKey.KeyType keyType) throws Exception {
        // Parse field 123: first 32 chars = IV, next 64 chars = ciphertext
        String ivHex = encryptedKeyHex.substring(0, 32);
        String ciphertextHex = encryptedKeyHex.substring(32, 96);

        byte[] iv = CryptoUtil.hexToBytes(ivHex);
        byte[] ciphertext = CryptoUtil.hexToBytes(ciphertextHex);

        log.debug("IV: {} ({} bytes)", ivHex, iv.length);
        log.debug("Ciphertext: {} chars ({} bytes)", ciphertextHex.length(), ciphertext.length);

        // Get current active key (TPK or TSK) for decryption
        String currentKeyHex = keyType == CryptoKey.KeyType.TPK ?
                              runtimeKeyManager.getTpkKey() :
                              runtimeKeyManager.getTskKey();

        byte[] currentKeyBytes = CryptoUtil.hexToBytes(currentKeyHex);

        // Use first 16 bytes for AES-128 (server uses AES-128-CBC)
        SecretKeySpec keySpec = new SecretKeySpec(currentKeyBytes, 0, 16, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        byte[] decrypted = cipher.doFinal(ciphertext);

        log.debug("Decrypted key: {} bytes", decrypted.length);

        return decrypted;
    }

    /**
     * Calculate SHA-256 checksum for key verification.
     * Per KEY_CHANGE_PROTOCOL.md:
     * - Algorithm: SHA-256
     * - Format: First 16 hex characters of hash
     */
    private String calculateChecksum(byte[] keyBytes) throws Exception {
        java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(keyBytes);

        // Take first 8 bytes (16 hex chars)
        String checksum = CryptoUtil.bytesToHex(hash).substring(0, 16).toUpperCase();

        log.debug("SHA-256 hash (first 16 chars): {}", checksum);

        return checksum;
    }

    /**
     * Map key type to ISO-8583 field 53 key type code.
     */
    private String getKeyTypeCode(CryptoKey.KeyType keyType) {
        return switch (keyType) {
            case TPK -> "01"; // PIN encryption key
            case TSK -> "02"; // MAC key
            case TMK -> "00"; // Master key
        };
    }
}
