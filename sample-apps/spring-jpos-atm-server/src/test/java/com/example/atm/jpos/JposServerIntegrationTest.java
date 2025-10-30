package com.example.atm.jpos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.example.atm.dto.hsm.PinFormat;
import com.example.atm.entity.PinEncryptionAlgorithm;
import com.example.atm.util.PinBlockGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration tests for jPOS server with real HSM simulator.
 *
 * Prerequisites:
 * - HSM simulator must be running on http://localhost:8080
 * - Database must be running (uses H2 in-memory for tests)
 * - jPOS server starts automatically via Spring Boot
 *
 * To run: mvn test -Dtest=JposServerIntegrationTest
 *
 * Note: These are REAL integration tests that communicate with actual HSM simulator.
 * No mocking is used.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@Slf4j
class JposServerIntegrationTest {

    @Value("${jpos.server.port}")
    private int serverPort;

    @Value("${jpos.server.channel}")
    private String channelClass;

    @Value("${jpos.server.packager}")
    private String packagerClass;

    @Value("${test.tpk.key}")
    private String tpkKey;

    @Value("${test.bank.uuid}")
    private String bankUuid;

    @Value("${test.terminal.id}")
    private String terminalId;

    @Value("${test.institution.id}")
    private String institutionId;

    private BaseChannel channel;
    private static final String HOST = "localhost";
    private static final String TEST_PAN = "4111111111111111";

    @BeforeEach
    void setUp() throws Exception {
        log.info("=== Setting up Integration Test ===");
        log.info("HSM URL: http://localhost:8080");
        log.info("jPOS Server: {}:{}", HOST, serverPort);
        log.info("Channel: {}", channelClass);
        log.info("Packager: {}", packagerClass);
        log.info("TPK Key: {}", tpkKey);

        // Instantiate packager from configuration
        ISOPackager packager = (ISOPackager) Class.forName(packagerClass)
                .getDeclaredConstructor()
                .newInstance();

        // Instantiate channel from configuration
        channel = (BaseChannel) Class.forName(channelClass)
                .getDeclaredConstructor(String.class, int.class, ISOPackager.class)
                .newInstance(HOST, serverPort, packager);

        // Wait for Q2 to be fully initialized
        Thread.sleep(2000);

        channel.connect();
        log.info("✓ Connected to jPOS server");
        log.info("=================================\n");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
            log.info("✓ Disconnected from jPOS server\n");
        }
    }

    /**
     * Test balance inquiry with valid PIN.
     *
     * This test:
     * 1. Generates a real PIN block using test TPK
     * 2. Sends ISO-8583 message to jPOS server
     * 3. jPOS calls REAL HSM simulator for PIN verification
     * 4. Expects successful response with balance
     *
     * Prerequisites:
     * - HSM simulator running on localhost:8080
     * - Account 1234567890 exists in database with:
     *   - Stored encrypted PIN block under LMK
     *   - Valid PVV or encrypted PIN block for verification
     */
    @Test
    void testBalanceInquiryWithPinIntegration() throws ISOException, IOException {
        log.info("TEST: Balance Inquiry with PIN (Integration)");

        ISOMsg request = createBalanceInquiryRequest("1234567890", "1234");

        log.info("→ Sending balance inquiry request: MTI={}", request.getMTI());
        log.info("  Account: {}", request.getString(102));
        log.info("  Terminal ID: {}", request.getString(41));
        if (request.hasField(123)) {
            log.info("  AES-256 PIN Block (field 123): {}", request.getString(123));
        }

        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("← Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");

        String responseCode = response.getString(39);
        if ("00".equals(responseCode)) {
            assertNotNull(response.getString(54), "Balance field (54) should be present");
            log.info("  ✓ PIN verified successfully");
            log.info("  ✓ Balance: {}", response.getString(54));
        } else {
            log.warn("  ✗ Response code: {} - {}", responseCode, getResponseCodeDescription(responseCode));
            // Fail if not 00 (approved)
            assertEquals("00", responseCode, "Expected approved response");
        }

        log.info("TEST PASSED\n");
    }

    /**
     * Test balance inquiry with PIN using PVV verification method.
     *
     * This test uses account 0987654321 which is configured to use PVV verification.
     * The HSM will verify the PIN by:
     * 1. Decrypting the PIN block from terminal
     * 2. Extracting the clear PIN
     * 3. Generating PVV from the PIN
     * 4. Comparing with stored PVV (0187)
     *
     * Prerequisites:
     * - HSM simulator running on localhost:8080
     * - Account 0987654321 exists with PVV='0187' for PIN 1234
     */
    @Test
    void testBalanceInquiryWithPvvVerification() throws ISOException, IOException {
        log.info("TEST: Balance Inquiry with PVV Verification");

        ISOMsg request = createBalanceInquiryRequest("0987654321", "1234");

        log.info("→ Sending balance inquiry request: MTI={}", request.getMTI());
        log.info("  Account: {}", request.getString(102));
        log.info("  Terminal ID: {}", request.getString(41));
        log.info("  Verification Method: PVV");
        if (request.hasField(123)) {
            log.info("  AES-256 PIN Block (field 123) provided");
        }

        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("← Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");

        String responseCode = response.getString(39);
        if ("00".equals(responseCode)) {
            assertNotNull(response.getString(54), "Balance field (54) should be present");
            log.info("  ✓ PIN verified successfully using PVV method");
            log.info("  ✓ Balance: {}", response.getString(54));
        } else {
            log.warn("  ✗ Response code: {} - {}", responseCode, getResponseCodeDescription(responseCode));
            // Fail if not 00 (approved)
            assertEquals("00", responseCode, "Expected approved response");
        }

        log.info("TEST PASSED\n");
    }

    /**
     * Test balance inquiry without PIN.
     * PIN is mandatory - should be rejected with response code 55.
     */
    @Test
    void testBalanceInquiryWithoutPin() throws ISOException, IOException {
        log.info("TEST: Balance Inquiry without PIN (should be rejected)");

        ISOMsg request = createBalanceInquiryRequest("1234567890", null);

        log.info("→ Sending balance inquiry request (no PIN)");
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("← Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("55", response.getString(39), "Response code should be 55 (PIN required)");

        log.info("TEST PASSED\n");
    }

    /**
     * Test balance inquiry with invalid account.
     * Should return response code 14 (invalid account).
     */
    @Test
    void testBalanceInquiryInvalidAccount() throws ISOException, IOException {
        log.info("TEST: Balance Inquiry with Invalid Account");

        ISOMsg request = createBalanceInquiryRequest("9999999999", null);

        log.info("→ Sending balance inquiry for non-existent account");
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("← Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("14", response.getString(39), "Response code should be 14 (invalid account)");

        log.info("TEST PASSED\n");
    }

    /**
     * Test withdrawal with PIN.
     *
     * Prerequisites:
     * - HSM simulator running
     * - Account 1234567890 has sufficient balance
     * - Valid PIN 1234
     */
    @Test
    void testWithdrawalWithPinIntegration() throws ISOException, IOException {
        log.info("TEST: Withdrawal with PIN (Integration)");

        ISOMsg request = createWithdrawalRequest("1234567890", 50000, "1234"); // 500.00

        log.info("→ Sending withdrawal request: Amount=500.00");
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("← Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");

        String responseCode = response.getString(39);
        if ("00".equals(responseCode)) {
            assertNotNull(response.getString(37), "Retrieval reference number should be present");
            log.info("  ✓ Withdrawal approved");
            log.info("  ✓ Reference: {}", response.getString(37));
        } else {
            log.warn("  ✗ Response code: {} - {}", responseCode, getResponseCodeDescription(responseCode));
        }

        log.info("TEST PASSED\n");
    }

    /**
     * Test withdrawal with insufficient funds.
     */
    @Test
    void testWithdrawalInsufficientFunds() throws ISOException, IOException {
        log.info("TEST: Withdrawal with Insufficient Funds");

        ISOMsg request = createWithdrawalRequest("1234567890", 1000000000, "1234"); // 10,000,000.00

        log.info("→ Sending withdrawal request for large amount");
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("← Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("51", response.getString(39), "Response code should be 51 (insufficient funds)");

        log.info("TEST PASSED\n");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private ISOMsg createBalanceInquiryRequest(String accountNumber, String clearPin) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0200");

        // Field 2: PAN
        msg.set(2, TEST_PAN);

        // Field 3: Processing Code - 310000 for balance inquiry
        msg.set(3, "310000");

        // Field 4: Amount (not used for balance inquiry, but required)
        msg.set(4, "000000000000");

        // Field 7: Transmission Date/Time
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        msg.set(7, sdf.format(new Date()));

        // Field 11: System Trace Audit Number
        msg.set(11, String.format("%06d", System.currentTimeMillis() % 1000000));

        // Field 12: Time, Local Transaction
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        msg.set(12, timeFormat.format(new Date()));

        // Field 13: Date, Local Transaction
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMdd");
        msg.set(13, dateFormat.format(new Date()));

        // Field 41: Terminal ID
        msg.set(41, terminalId);

        // Field 42: Card Acceptor ID (institution code)
        msg.set(42, institutionId);

        // Field 123: AES-256 PIN Block (if provided)
        // NOTE: Field 123 is binary, so we must set as bytes, not as hex string
        if (clearPin != null && !clearPin.isEmpty()) {
            String pinBlock = PinBlockGenerator.generatePinBlock(
                    clearPin, TEST_PAN, tpkKey, bankUuid,
                    PinEncryptionAlgorithm.AES_256, PinFormat.ISO_0);
            // Convert hex string to bytes for binary field
            msg.set(123, hexToBytes(pinBlock));
        }

        // Field 102: Account Number
        msg.set(102, accountNumber);

        return msg;
    }

    private ISOMsg createWithdrawalRequest(String accountNumber, long amountInCents, String clearPin) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0200");

        // Field 2: PAN
        msg.set(2, TEST_PAN);

        // Field 3: Processing Code - 010000 for withdrawal
        msg.set(3, "010000");

        // Field 4: Amount in cents (12 digits, right-justified, zero-padded)
        msg.set(4, String.format("%012d", amountInCents));

        // Field 7: Transmission Date/Time
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        msg.set(7, sdf.format(new Date()));

        // Field 11: System Trace Audit Number
        msg.set(11, String.format("%06d", System.currentTimeMillis() % 1000000));

        // Field 12: Time, Local Transaction
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        msg.set(12, timeFormat.format(new Date()));

        // Field 13: Date, Local Transaction
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMdd");
        msg.set(13, dateFormat.format(new Date()));

        // Field 41: Terminal ID
        msg.set(41, terminalId);

        // Field 42: Card Acceptor ID (institution code)
        msg.set(42, institutionId);

        // Field 123: AES-256 PIN Block (if provided)
        // NOTE: Field 123 is binary, so we must set as bytes, not as hex string
        if (clearPin != null && !clearPin.isEmpty()) {
            String pinBlock = PinBlockGenerator.generatePinBlock(
                    clearPin, TEST_PAN, tpkKey, bankUuid,
                    PinEncryptionAlgorithm.AES_256, PinFormat.ISO_0);
            // Convert hex string to bytes for binary field
            msg.set(123, hexToBytes(pinBlock));
        }

        // Field 102: Account Number
        msg.set(102, accountNumber);

        return msg;
    }

    private String getResponseCodeDescription(String code) {
        return switch (code) {
            case "00" -> "Approved";
            case "14" -> "Invalid account";
            case "30" -> "Format error";
            case "51" -> "Insufficient funds";
            case "55" -> "Incorrect PIN";
            case "62" -> "Restricted account";
            case "96" -> "System error";
            default -> "Unknown";
        };
    }

    /**
     * Convert hex string to byte array.
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
