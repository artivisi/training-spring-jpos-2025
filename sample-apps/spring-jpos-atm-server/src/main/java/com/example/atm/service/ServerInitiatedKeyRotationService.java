package com.example.atm.service;

import com.example.atm.entity.CryptoKey;
import com.example.atm.jpos.service.ChannelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

/**
 * Service for server-initiated key rotation.
 * Sends 0800 network management messages to terminals to trigger key change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServerInitiatedKeyRotationService {

    private final ChannelRegistry channelRegistry;

    /**
     * Initiate key rotation for a specific terminal.
     * Sends 0800 notification message to the terminal.
     *
     * @param terminalId Terminal identifier (e.g., "TRM-ISS001-ATM-001")
     * @param keyType Key type to rotate (TPK or TSK)
     * @return true if notification sent successfully, false otherwise
     */
    public boolean initiateKeyRotation(String terminalId, CryptoKey.KeyType keyType) {
        log.info("Initiating server-side key rotation: terminalId={}, keyType={}", terminalId, keyType);

        // Get channel for terminal
        ISOChannel channel = channelRegistry.getChannel(terminalId);
        if (channel == null) {
            log.error("Cannot initiate key rotation: terminal not connected: {}", terminalId);
            return false;
        }

        try {
            // Build 0800 notification message
            ISOMsg notification = buildKeyRotationNotification(terminalId, keyType);

            // Send notification to terminal
            log.info("Sending key rotation notification to terminal: terminalId={}, keyType={}",
                    terminalId, keyType);
            channel.send(notification);

            log.info("Key rotation notification sent successfully: terminalId={}, keyType={}",
                    terminalId, keyType);
            return true;

        } catch (Exception e) {
            log.error("Failed to send key rotation notification: terminalId={}, keyType={}, error={}",
                    terminalId, keyType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Build 0800 network management message for key rotation notification.
     * Operation code 07 indicates server-initiated key change notification.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type (TPK or TSK)
     * @return ISO message ready to send
     */
    private ISOMsg buildKeyRotationNotification(String terminalId, CryptoKey.KeyType keyType) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0800");

        // Field 11: STAN - use timestamp-based sequence
        String stan = String.format("%06d", System.currentTimeMillis() % 1000000);
        msg.set(11, stan);

        // Field 41: Terminal ID (extract from full terminal ID)
        String[] parts = terminalId.split("-");
        String termId = parts.length > 2 ? parts[parts.length - 1] : terminalId;
        msg.set(41, String.format("%-16s", termId));  // Left-padded to 16 chars

        // Field 42: Card Acceptor ID (institution code)
        if (parts.length > 2) {
            String institution = parts[0] + "-" + parts[1];
            msg.set(42, String.format("%-15s", institution));
        }

        // Field 53: Security Related Control Information
        // Operation code 07 = Server-initiated key change notification
        // Next 2 digits = key type: 01=TPK, 02=TSK
        String operationCode = "07";
        String keyTypeCode = keyType == CryptoKey.KeyType.TPK ? "01" : "02";
        msg.set(53, operationCode + keyTypeCode + "000000000000");

        // Field 70: Network Management Information Code
        // 301 = Key change request
        msg.set(70, "301");

        log.debug("Built key rotation notification: MTI={}, STAN={}, Field53={}, Field70={}",
                msg.getMTI(), stan, msg.getString(53), msg.getString(70));

        return msg;
    }

    /**
     * Check if a terminal is currently connected.
     *
     * @param terminalId Terminal identifier
     * @return true if terminal has active connection
     */
    public boolean isTerminalConnected(String terminalId) {
        return channelRegistry.isConnected(terminalId);
    }
}
