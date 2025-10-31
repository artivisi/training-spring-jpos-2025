package com.example.atm.jpos.participant;

import com.example.atm.dto.rotation.KeyRotationConfirmation;
import com.example.atm.entity.CryptoKey;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.CryptoKeyService;
import com.example.atm.service.HsmClient;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
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
     * Get terminal ID from context or ISO message fields 42 + 41.
     * First checks if KeyChangeParticipant stored it in context,
     * then extracts from REQUEST message if needed.
     */
    private String getTerminalId(Context ctx) {
        try {
            // First check if KeyChangeParticipant stored it in context
            String contextTerminalId = (String) ctx.get("KEY_CHANGE_TERMINAL_ID");
            if (contextTerminalId != null && !contextTerminalId.trim().isEmpty()) {
                log.debug("Using terminal ID from context: {}", contextTerminalId);
                return contextTerminalId.trim();
            }

            // Extract from REQUEST message
            ISOMsg msg = (ISOMsg) ctx.get("REQUEST");
            if (msg == null) {
                log.warn("No ISO message in context, using default terminal ID");
                return "TRM-ISS001-ATM-001";
            }

            // Build full terminal ID from field 42 (institution) + field 41 (terminal)
            String cardAcceptorId = msg.getString(42);  // e.g., "TRM-ISS001"
            String terminalId = msg.getString(41);       // e.g., "ATM-001"

            if (terminalId == null || terminalId.trim().isEmpty()) {
                log.warn("Terminal ID not found in field 41, using default");
                return "TRM-ISS001-ATM-001";
            }

            String fullTerminalId = (cardAcceptorId != null && !cardAcceptorId.trim().isEmpty())
                    ? cardAcceptorId.trim() + "-" + terminalId.trim()
                    : terminalId.trim();

            log.debug("Extracted terminal ID from message: {}", fullTerminalId);
            return fullTerminalId;

        } catch (Exception e) {
            log.error("Error extracting terminal ID: {}", e.getMessage());
            return "TRM-ISS001-ATM-001";
        }
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
            // Check if terminal explicitly confirmed key installation (operation codes 03/04)
            Boolean keyChangeConfirmed = (Boolean) ctx.get("KEY_CHANGE_CONFIRMED");
            if (keyChangeConfirmed != null && keyChangeConfirmed) {
                CryptoKey.KeyType keyType = (CryptoKey.KeyType) ctx.get("KEY_CHANGE_TYPE");
                String terminalId = (String) ctx.get("KEY_CHANGE_TERMINAL_ID");

                if (keyType != null && terminalId != null) {
                    // Get the PENDING key version for this terminal/keyType
                    CryptoKey pendingKey = getCryptoKeyService()
                            .getPendingKey(terminalId, keyType);

                    if (pendingKey != null) {
                        log.info("Explicit confirmation received for {} key installation: terminal={}",
                                keyType, terminalId);
                        activatePendingKey(terminalId, keyType, pendingKey.getKeyVersion());
                    } else {
                        log.warn("No PENDING key found for explicit confirmation: terminal={}, keyType={}",
                                terminalId, keyType);
                    }
                } else {
                    log.warn("Missing keyType or terminalId in context for key confirmation");
                }
            }

            // Check if terminal reported key installation failure (operation codes 05/06)
            Boolean keyChangeFailed = (Boolean) ctx.get("KEY_CHANGE_FAILED");
            if (keyChangeFailed != null && keyChangeFailed) {
                CryptoKey.KeyType keyType = (CryptoKey.KeyType) ctx.get("KEY_CHANGE_TYPE");
                String terminalId = (String) ctx.get("KEY_CHANGE_TERMINAL_ID");
                String failureReason = (String) ctx.get("KEY_CHANGE_FAILURE_REASON");

                if (keyType != null && terminalId != null) {
                    handleKeyInstallationFailure(terminalId, keyType, failureReason);
                } else {
                    log.warn("Missing keyType or terminalId in context for key failure");
                }
            }

        } catch (Exception e) {
            log.error("Error in key activation/failure handling: {}", e.getMessage(), e);
            // Don't fail the transaction - key operations should not affect the transaction
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
     * Handle key installation failure reported by terminal.
     * Removes PENDING key from database and notifies HSM.
     */
    private void handleKeyInstallationFailure(String terminalId, CryptoKey.KeyType keyType, String failureReason) {
        log.error("Handling key installation failure: terminal={}, keyType={}, reason={}",
                terminalId, keyType, failureReason);

        try {
            // Remove PENDING key from database
            getCryptoKeyService().removePendingKey(terminalId, keyType);

            log.info("Removed PENDING {} key after installation failure: terminal={}",
                    keyType, terminalId);

            // Notify HSM that rotation failed (asynchronously - don't block)
            notifyHsmFailureAsync(terminalId, keyType, failureReason);

        } catch (Exception e) {
            log.error("Failed to handle key installation failure: terminal={}, keyType={}, error={}",
                    terminalId, keyType, e.getMessage(), e);
            // Don't throw - failure handling should not block transaction
        }
    }

    /**
     * Notify HSM that key rotation failed asynchronously.
     */
    private void notifyHsmFailureAsync(String terminalId, CryptoKey.KeyType keyType, String reason) {
        // For now, just log - HSM notification for failure not implemented yet
        // In production, this should:
        // 1. Call HSM API to report rotation failure
        // 2. Include failure reason
        // 3. Allow HSM to clean up its records
        log.warn("HSM failure notification not implemented: terminal={}, keyType={}, reason={}",
                terminalId, keyType, reason);

        // TODO: Implement HSM failure notification
        // Example:
        // try {
        //     KeyRotationFailure failure = KeyRotationFailure.builder()
        //             .terminalId(terminalId)
        //             .keyType(keyType)
        //             .failureReason(reason)
        //             .build();
        //     getHsmClient().notifyRotationFailure(terminalId, failure);
        // } catch (Exception e) {
        //     log.error("Failed to notify HSM of rotation failure", e);
        // }
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
