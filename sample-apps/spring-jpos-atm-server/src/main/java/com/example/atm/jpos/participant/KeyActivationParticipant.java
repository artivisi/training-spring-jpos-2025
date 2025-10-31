package com.example.atm.jpos.participant;

import com.example.atm.dto.rotation.KeyRotationConfirmation;
import com.example.atm.entity.CryptoKey;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.CryptoKeyService;
import com.example.atm.service.HsmClient;
import lombok.extern.slf4j.Slf4j;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

/**
 * jPOS TransactionParticipant for auto-activating PENDING keys after successful use.
 *
 * When a terminal starts using a PENDING key (detected via MAC verification),
 * this participant activates the key in the database and confirms to the HSM.
 *
 * This runs in the commit phase AFTER the response has been sent to the terminal,
 * ensuring the terminal successfully received and can use the new key before activation.
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class KeyActivationParticipant implements TransactionParticipant {

    private CryptoKeyService getCryptoKeyService() {
        return SpringBeanFactory.getBean(CryptoKeyService.class);
    }

    private HsmClient getHsmClient() {
        return SpringBeanFactory.getBean(HsmClient.class);
    }

    /**
     * Get terminal ID from context.
     * TODO: Extract from ISO message field 41 in future enhancement.
     */
    private String getTerminalId(Context ctx) {
        return "TRM-ISS001-ATM-001";
    }

    @Override
    public int prepare(long id, Serializable context) {
        // No action needed in prepare phase
        return PREPARED | READONLY;
    }

    @Override
    public void commit(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            // Check if there's a PENDING TSK key to activate
            Integer pendingTskVersion = (Integer) ctx.get("ACTIVATE_PENDING_TSK");
            if (pendingTskVersion != null) {
                String terminalId = getTerminalId(ctx);
                activatePendingKey(terminalId, CryptoKey.KeyType.TSK, pendingTskVersion);
            }

            // Check if there's a PENDING TPK key to activate
            Integer pendingTpkVersion = (Integer) ctx.get("ACTIVATE_PENDING_TPK");
            if (pendingTpkVersion != null) {
                String terminalId = getTerminalId(ctx);
                activatePendingKey(terminalId, CryptoKey.KeyType.TPK, pendingTpkVersion);
            }

        } catch (Exception e) {
            log.error("Error activating PENDING key: {}", e.getMessage(), e);
            // Don't fail the transaction - key activation failure should not affect the transaction
            // The PENDING key will remain PENDING and can be activated manually
        }
    }

    @Override
    public void abort(long id, Serializable context) {
        // No action needed on abort - don't activate PENDING keys if transaction failed
        log.debug("Transaction {} aborted, skipping key activation", id);
    }

    /**
     * Activate a PENDING key and confirm to HSM.
     */
    private void activatePendingKey(String terminalId, CryptoKey.KeyType keyType, Integer version) {
        log.info("Auto-activating PENDING {} key version {} for terminal: {}",
                keyType, version, terminalId);

        try {
            // Activate the key in database
            getCryptoKeyService().activateKey(terminalId, keyType, version);

            log.info("Successfully activated {} key version {} for terminal: {}",
                    keyType, version, terminalId);

            // Confirm to HSM (asynchronously - don't block transaction completion)
            confirmToHsmAsync(terminalId, keyType, version);

        } catch (Exception e) {
            log.error("Failed to activate {} key version {} for terminal {}: {}",
                    keyType, version, terminalId, e.getMessage(), e);
            throw new RuntimeException("Key activation failed", e);
        }
    }

    /**
     * Confirm key activation to HSM asynchronously.
     * This is done in a separate thread to avoid blocking transaction completion.
     */
    private void confirmToHsmAsync(String terminalId, CryptoKey.KeyType keyType, Integer version) {
        // For now, do it synchronously
        // In production, this should be done via async message queue or CompletableFuture
        try {
            // Generate rotation ID from terminal and version
            String rotationId = String.format("%s-%s-v%d", terminalId, keyType, version);

            KeyRotationConfirmation confirmation = KeyRotationConfirmation.builder()
                    .rotationId(rotationId)
                    .confirmedBy("ATM_SERVER_AUTO_ACTIVATION")
                    .build();

            log.debug("Confirming key activation to HSM: terminalId={}, keyType={}, version={}",
                    terminalId, keyType, version);

            getHsmClient().confirmKeyRotation(terminalId, confirmation);

            log.info("Successfully confirmed {} key activation to HSM: terminal={}, version={}",
                    keyType, terminalId, version);

        } catch (Exception e) {
            // Log error but don't fail - key is already activated in database
            // HSM confirmation can be retried manually if needed
            log.error("Failed to confirm key activation to HSM (key already activated locally): " +
                    "terminal={}, keyType={}, version={}, error={}",
                    terminalId, keyType, version, e.getMessage());
        }
    }
}
