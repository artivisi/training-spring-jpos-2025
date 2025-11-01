package com.example.atm.jpos.listener;

import com.example.atm.jpos.config.SpringBeanFactory;
import com.example.atm.service.KeyChangeService;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;

/**
 * Listener for server-initiated key rotation notifications.
 * Handles incoming 0800 messages with operation code 07 from the server.
 *
 * When server sends key rotation notification:
 * 1. Acknowledge with 0810
 * 2. Automatically initiate key change (operation 01/02)
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 * Uses SpringBeanFactory to access Spring beans.
 */
@Slf4j
public class KeyRotationNotificationListener implements ISORequestListener {

    private KeyChangeService getKeyChangeService() {
        return SpringBeanFactory.getBean(KeyChangeService.class);
    }

    @Override
    public boolean process(ISOSource source, ISOMsg m) {
        try {
            log.info("KeyRotationNotificationListener.process() called - MTI={}",
                m != null ? m.getMTI() : "null");
            String mti = m.getMTI();

            // Only process network management messages (0800)
            if (!"0800".equals(mti)) {
                log.debug("Not an 0800 message, skipping: MTI={}", mti);
                return false; // Not handled by this listener
            }

            // Check if this is a key rotation notification
            String securityControl = m.getString(53);
            log.debug("Field 53 value: {}", securityControl);

            if (securityControl == null || securityControl.length() < 2) {
                log.debug("No field 53 or too short, not a key change message");
                return false; // Not a key change message
            }

            String operationCode = securityControl.substring(0, 2);
            log.info("Detected operation code: {}", operationCode);

            // Operation code 07 = Server-initiated key change notification
            if ("07".equals(operationCode)) {
                log.info("Handling server-initiated key rotation notification");
                handleKeyRotationNotification(source, m);
                return true; // Handled
            } else {
                log.debug("Operation code {} not handled by this listener", operationCode);
            }

        } catch (Exception e) {
            log.error("Error processing key rotation notification: {}", e.getMessage(), e);
        }

        return false; // Not handled
    }

    /**
     * Handle server-initiated key rotation notification.
     *
     * @param source ISO source for sending response
     * @param request The 0800 notification message
     */
    private void handleKeyRotationNotification(ISOSource source, ISOMsg request) {
        try {
            log.info("Received key rotation notification from server");

            // Extract key type from field 53
            String securityControl = request.getString(53);
            String keyTypeCode = securityControl.substring(2, 4); // Chars 2-3
            String keyType = "01".equals(keyTypeCode) ? "TPK" : "TSK";

            log.info("Server requesting key rotation: keyType={}", keyType);

            // Send acknowledgment (0810)
            ISOMsg response = buildAcknowledgment(request);
            source.send(response);

            log.info("Sent acknowledgment to server, initiating key change");

            // Automatically initiate key change in background thread
            // to avoid blocking the listener
            new Thread(() -> {
                try {
                    // Small delay to ensure acknowledgment is sent
                    Thread.sleep(1000);

                    log.info("Initiating automatic key change: keyType={}", keyType);
                    getKeyChangeService().changeKey(keyType);

                } catch (Exception e) {
                    log.error("Failed to initiate automatic key change: {}", e.getMessage(), e);
                }
            }, "auto-key-change-" + keyType).start();

        } catch (Exception e) {
            log.error("Failed to handle key rotation notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Build acknowledgment response (0810) for key rotation notification.
     */
    private ISOMsg buildAcknowledgment(ISOMsg request) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0810");

        // Field 39: Response code = "00" (approved)
        response.set(39, "00");

        // Echo fields from request
        if (request.hasField(11)) {
            response.set(11, request.getString(11)); // STAN
        }
        if (request.hasField(41)) {
            response.set(41, request.getString(41)); // Terminal ID
        }
        if (request.hasField(53)) {
            response.set(53, request.getString(53)); // Security control
        }

        log.debug("Built acknowledgment: MTI={}, responseCode=00", response.getMTI());

        return response;
    }
}
