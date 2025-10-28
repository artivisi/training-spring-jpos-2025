package com.example.atm.jpos;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.BaseChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class JposServerTest {

    @Value("${jpos.server.port}")
    private int serverPort;

    @Value("${jpos.server.channel}")
    private String channelClass;

    @Value("${jpos.server.packager}")
    private String packagerClass;

    private BaseChannel channel;
    private static final String HOST = "localhost";

    @BeforeEach
    void setUp() throws Exception {
        log.info("Setting up test channel");
        log.info("Channel class: {}", channelClass);
        log.info("Packager class: {}", packagerClass);

        // Instantiate packager from configuration
        ISOPackager packager = (ISOPackager) Class.forName(packagerClass)
                .getDeclaredConstructor()
                .newInstance();

        // Instantiate channel from configuration using constructor(String host, int port, ISOPackager packager)
        channel = (BaseChannel) Class.forName(channelClass)
                .getDeclaredConstructor(String.class, int.class, ISOPackager.class)
                .newInstance(HOST, serverPort, packager);

        // Wait a bit for Q2 to start
        Thread.sleep(2000);

        channel.connect();
        log.info("Connected to jPOS server at {}:{} using channel={} packager={}",
                 HOST, serverPort, channelClass, packagerClass);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
            log.info("Disconnected from jPOS server");
        }
    }

    @Test
    void testBalanceInquiry() throws ISOException, IOException {
        log.info("Testing balance inquiry");

        ISOMsg request = createBalanceInquiryRequest("1234567890");

        log.info("Sending balance inquiry request: MTI={}", request.getMTI());
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("00", response.getString(39), "Response code should be 00 (approved)");
        assertNotNull(response.getString(54), "Balance field (54) should be present");

        log.info("Balance inquiry test passed, current balance is : {}", response.getString(54));
    }

    @Test
    void testBalanceInquiryInvalidAccount() throws ISOException, IOException {
        log.info("Testing balance inquiry with invalid account");

        ISOMsg request = createBalanceInquiryRequest("9999999999");

        log.info("Sending balance inquiry request for invalid account");
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("14", response.getString(39), "Response code should be 14 (invalid account)");

        log.info("Invalid account test passed");
    }

    @Test
    void testWithdrawal() throws ISOException, IOException {
        log.info("Testing cash withdrawal");

        ISOMsg request = createWithdrawalRequest("1234567890", 50000); // 500.00

        log.info("Sending withdrawal request: MTI={}", request.getMTI());
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("00", response.getString(39), "Response code should be 00 (approved)");
        assertNotNull(response.getString(37), "Retrieval reference number should be present");

        log.info("Withdrawal test passed");
    }

    @Test
    void testWithdrawalInsufficientFunds() throws ISOException, IOException {
        log.info("Testing withdrawal with insufficient funds");

        ISOMsg request = createWithdrawalRequest("1234567890", 1000000000); // 10,000,000.00

        log.info("Sending withdrawal request for large amount");
        channel.send(request);

        ISOMsg response = channel.receive();
        assertNotNull(response, "Response should not be null");

        log.info("Received response: MTI={} RC={}",
                response.getMTI(), response.getString(39));

        assertEquals("0210", response.getMTI(), "Response MTI should be 0210");
        assertEquals("51", response.getString(39), "Response code should be 51 (insufficient funds)");

        log.info("Insufficient funds test passed");
    }

    private ISOMsg createBalanceInquiryRequest(String accountNumber) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0200");

        // Field 2: PAN
        msg.set(2, "4111111111111111");

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
        msg.set(41, "ATM00001");

        // Field 102: Account Number
        msg.set(102, accountNumber);

        return msg;
    }

    private ISOMsg createWithdrawalRequest(String accountNumber, long amountInCents) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0200");

        // Field 2: PAN
        msg.set(2, "4111111111111111");

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
        msg.set(41, "ATM00001");

        // Field 102: Account Number
        msg.set(102, accountNumber);

        return msg;
    }
}
