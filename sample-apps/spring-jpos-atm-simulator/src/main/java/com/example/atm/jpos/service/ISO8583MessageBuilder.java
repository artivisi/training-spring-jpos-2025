package com.example.atm.jpos.service;

import com.example.atm.dto.TransactionRequest;
import com.example.atm.util.AesCmacUtil;
import com.example.atm.util.AesPinBlockUtil;
import com.example.atm.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.BASE24Packager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

@Service
@Slf4j
public class ISO8583MessageBuilder {

    @Value("${crypto.tpk.key}")
    private String tpkKey;

    @Value("${crypto.tsk.key}")
    private String tskKey;

    @Value("${crypto.bank.uuid}")
    private String bankUuid;

    @Value("${terminal.id}")
    private String terminalId;

    @Value("${terminal.institution.id}")
    private String institutionId;

    private final Random random = new Random();
    private final BASE24Packager packager = new BASE24Packager();

    public ISOMsg buildTransactionRequest(TransactionRequest request, String accountNumber)
        throws Exception {

        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI("0200");

        // Field 2: PAN
        msg.set(2, request.getPan());

        // Field 3: Processing Code
        String processingCode = getProcessingCode(request.getType());
        msg.set(3, processingCode);

        // Field 4: Amount
        if (request.getAmount() != null) {
            String amountStr = formatAmount(request.getAmount());
            msg.set(4, amountStr);
        } else {
            msg.set(4, "000000000000");
        }

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

        // Field 102: Account Number
        msg.set(102, accountNumber);

        // Field 123: AES-256 PIN Block (binary field)
        if (request.getPin() != null && !request.getPin().isEmpty()) {
            byte[] clearPinBlock = AesPinBlockUtil.buildIso0PinBlock(
                    request.getPin(), request.getPan());
            byte[] tpkMasterKeyBytes = CryptoUtil.hexToBytes(tpkKey);
            byte[] encryptedPinBlock = AesPinBlockUtil.encryptPinBlock(
                    clearPinBlock, tpkMasterKeyBytes, bankUuid);
            msg.set(123, encryptedPinBlock);
            log.debug("  Field 123 (AES-256 PIN Block): {} bytes", encryptedPinBlock.length);
        }

        // Add MAC to message (field 64)
        addMacToMessage(msg);

        log.info("Built ISO 8583 message:");
        log.info("  MTI: {}", msg.getMTI());
        log.info("  Field 2 (PAN): {}", msg.getString(2));
        log.info("  Field 3 (Processing Code): {}", msg.getString(3));
        log.info("  Field 4 (Amount): {}", msg.getString(4));
        log.info("  Field 7 (Transmission Date/Time): {}", msg.getString(7));
        log.info("  Field 11 (STAN): {}", msg.getString(11));
        log.info("  Field 41 (Terminal ID): {}", msg.getString(41));
        log.info("  Field 102 (Account Number): {}", msg.getString(102));
        if (msg.hasField(123)) {
            log.info("  Field 123 (AES-256 PIN Block): present");
        }

        return msg;
    }

    private void addMacToMessage(ISOMsg msg) throws Exception {
        msg.setPackager(packager);

        byte[] macData = msg.pack();

        byte[] tskMasterKeyBytes = CryptoUtil.hexToBytes(tskKey);

        byte[] mac = AesCmacUtil.generateMacWithKeyDerivation(
                macData, tskMasterKeyBytes, bankUuid);

        msg.set(64, mac);

        log.debug("Added MAC to message: {} bytes", mac.length);
    }

    private String getProcessingCode(TransactionRequest.TransactionType type) {
        return switch (type) {
            case BALANCE -> "310000";
            case WITHDRAWAL -> "010000";
        };
    }

    private String formatAmount(BigDecimal amount) {
        long amountCents = amount.multiply(new BigDecimal("100")).longValue();
        return String.format("%012d", amountCents);
    }

    public boolean verifyResponseMac(ISOMsg response) throws Exception {
        if (!response.hasField(64)) {
            log.warn("Response does not contain MAC field (64)");
            return false;
        }

        byte[] receivedMac = response.getBytes(64);

        ISOMsg clonedMsg = (ISOMsg) response.clone();
        clonedMsg.unset(64);
        clonedMsg.setPackager(packager);

        byte[] macData = clonedMsg.pack();
        byte[] tskMasterKeyBytes = CryptoUtil.hexToBytes(tskKey);

        log.debug("MAC verification details:");
        log.debug("  MAC data length: {} bytes", macData.length);
        log.debug("  MAC data (first 32 bytes): {}", CryptoUtil.bytesToHex(java.util.Arrays.copyOf(macData, Math.min(32, macData.length))));
        log.debug("  TSK key: {}", tskKey);
        log.debug("  Bank UUID: {}", bankUuid);

        byte[] calculatedMac = AesCmacUtil.generateMacWithKeyDerivation(
                macData, tskMasterKeyBytes, bankUuid);

        boolean isValid = java.util.Arrays.equals(receivedMac, calculatedMac);

        if (isValid) {
            log.debug("Response MAC verification successful");
        } else {
            log.error("Response MAC verification failed!");
            log.debug("  Received MAC: {}", CryptoUtil.bytesToHex(receivedMac));
            log.debug("  Calculated MAC: {}", CryptoUtil.bytesToHex(calculatedMac));
        }

        return isValid;
    }

    public BigDecimal parseBalanceFromResponse(ISOMsg response) throws Exception {
        if (!response.hasField(54)) {
            log.warn("Response does not contain balance field (54)");
            return null;
        }

        String field54 = response.getString(54);
        log.debug("Field 54 (Additional Amounts): {}", field54);

        // Format: "001360" (6 chars) + 12-digit balance = 18 chars total
        if (field54.length() >= 18) {
            String balanceStr = field54.substring(6, 18);
            long balanceCents = Long.parseLong(balanceStr);
            BigDecimal balance = new BigDecimal(balanceCents).divide(new BigDecimal("100"));
            log.debug("Parsed balance: {}", balance);
            return balance;
        }

        return null;
    }
}
