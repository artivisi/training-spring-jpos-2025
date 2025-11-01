package com.example.atm.jpos.service;

import com.example.atm.jpos.config.SpringBeanFactory;
import com.example.atm.service.KeyChangeService;
import com.example.atm.util.TerminalIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.q2.QBeanSupport;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;

/**
 * Q2 service that monitors the unhandled message queue for server-initiated messages.
 * Processes messages that don't match pending MUX requests, such as:
 * - Server-initiated key rotation notifications (0800 with field 53 operation 07)
 *
 * This class is managed by jPOS Q2 and uses SpringBeanFactory to access Spring beans.
 */
@Slf4j
public class UnhandledMessageService extends QBeanSupport implements Runnable {

    private Space space;
    private String unhandledQueue;
    private volatile boolean running = true;
    private Thread processorThread;

    @Override
    protected void initService() throws Exception {
        log.info("Initializing UnhandledMessageService");

        // Get space and unhandled queue from configuration
        String spaceName = cfg.get("space", "tspace:default");
        unhandledQueue = cfg.get("queue", "atm-unhandled");

        space = SpaceFactory.getSpace(spaceName);
        log.info("Monitoring unhandled messages on queue: {}", unhandledQueue);
    }

    /**
     * Get terminal ID from Spring configuration via SignOnService.
     */
    private String getTerminalId() {
        try {
            SignOnService signOnService = SpringBeanFactory.getBean(SignOnService.class);
            return signOnService.getTerminalId();
        } catch (Exception e) {
            log.error("Failed to get terminal ID from Spring: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get institution ID from Spring configuration via SignOnService.
     */
    private String getInstitutionId() {
        try {
            SignOnService signOnService = SpringBeanFactory.getBean(SignOnService.class);
            return signOnService.getInstitutionId();
        } catch (Exception e) {
            log.error("Failed to get institution ID from Spring: {}", e.getMessage());
            return null;
        }
    }

    @Override
    protected void startService() throws Exception {
        log.info("Starting UnhandledMessageService");
        processorThread = new Thread(this, "unhandled-message-processor");
        processorThread.start();
    }

    @Override
    protected void stopService() throws Exception {
        log.info("Stopping UnhandledMessageService");
        running = false;
        if (processorThread != null) {
            processorThread.interrupt();
            processorThread.join(5000);
        }
    }

    @Override
    public void run() {
        log.info("UnhandledMessageService processor thread started");

        while (running) {
            try {
                // Wait for unhandled messages (30 second timeout to allow checking running flag)
                Object obj = space.in(unhandledQueue, 30000);

                if (obj != null && running) {
                    if (obj instanceof ISOMsg) {
                        ISOMsg msg = (ISOMsg) obj;
                        log.info("Processing unhandled message: MTI={}", msg.getMTI());
                        processUnhandledMessage(msg);
                    } else {
                        log.warn("Unexpected object in unhandled queue: {}", obj.getClass().getName());
                    }
                }

            } catch (Exception e) {
                if (running) {
                    log.error("Error processing unhandled message: {}", e.getMessage(), e);
                    // Brief pause before retrying
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        log.warn("Processor thread interrupted during error recovery");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.info("UnhandledMessageService processor thread stopped");
    }

    /**
     * Process an unhandled message from the server.
     *
     * @param msg The ISO message to process
     */
    private void processUnhandledMessage(ISOMsg msg) {
        try {
            String mti = msg.getMTI();
            log.info("Unhandled message MTI: {}", mti);

            // Only process network management messages (0800)
            if (!"0800".equals(mti)) {
                log.debug("Not a network management message, ignoring: MTI={}", mti);
                return;
            }

            // Check if this is a key rotation notification (field 53 with operation 07)
            String securityControl = msg.getString(53);
            log.debug("Field 53 value: {}", securityControl);

            if (securityControl == null || securityControl.length() < 2) {
                log.debug("No field 53 or too short, not a key change message");
                return;
            }

            String operationCode = securityControl.substring(0, 2);
            log.info("Detected operation code: {}", operationCode);

            // Operation code 07 = Server-initiated key change notification
            if ("07".equals(operationCode)) {
                log.info("Handling server-initiated key rotation notification");
                handleKeyRotationNotification(msg);
            } else {
                log.debug("Operation code {} not handled by this service", operationCode);
            }

        } catch (Exception e) {
            log.error("Error processing unhandled message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle server-initiated key rotation notification.
     *
     * @param msg The 0800 notification message
     */
    private void handleKeyRotationNotification(ISOMsg msg) {
        try {
            log.info("Received key rotation notification from server");

            // Extract key type from field 53
            String securityControl = msg.getString(53);
            String keyTypeCode = securityControl.substring(2, 4); // Chars 2-3
            String keyType = "01".equals(keyTypeCode) ? "TPK" : "TSK";

            log.info("Server requesting key rotation: keyType={}", keyType);

            // Send acknowledgment (0810) back to server
            sendAcknowledgment(msg);

            log.info("Sent acknowledgment to server, initiating key change");

            // Automatically initiate key change in background thread
            // to avoid blocking the processor
            new Thread(() -> {
                try {
                    // Small delay to ensure acknowledgment is sent
                    Thread.sleep(1000);

                    log.info("Initiating automatic key change: keyType={}", keyType);
                    KeyChangeService keyChangeService = SpringBeanFactory.getBean(KeyChangeService.class);
                    keyChangeService.changeKey(keyType);

                } catch (Exception e) {
                    log.error("Failed to initiate automatic key change: {}", e.getMessage(), e);
                }
            }, "auto-key-change-" + keyType).start();

        } catch (Exception e) {
            log.error("Failed to handle key rotation notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Send acknowledgment response (0810) for key rotation notification.
     * Uses MuxService to send the response.
     */
    private void sendAcknowledgment(ISOMsg request) {
        try {
            ISOMsg response = (ISOMsg) request.clone();
            response.setMTI("0810");

            // Field 39: Response code = "00" (approved)
            response.set(39, "00");

            // Echo STAN from request
            if (request.hasField(11)) {
                response.set(11, request.getString(11)); // STAN
            }

            // Use our own configured terminal and institution IDs (NOT echoing server's values)
            String terminalId = getTerminalId();
            String institutionId = getInstitutionId();
            TerminalIdUtil.setTerminalIdFields(response, terminalId, institutionId);

            // Echo security control from request
            if (request.hasField(53)) {
                response.set(53, request.getString(53)); // Security control
            }

            log.debug("Built acknowledgment: MTI={}, responseCode=00, terminalId={}, institutionId={}",
                    response.getMTI(), terminalId, institutionId);

            // Send via MuxService
            MuxService muxService = SpringBeanFactory.getBean(MuxService.class);

            // Put the response directly on the send queue (bypassing MUX request/response)
            // since this is an unsolicited response
            space.out("atm-send", response);
            log.info("Acknowledgment sent to server");

        } catch (ISOException e) {
            log.error("Failed to build acknowledgment: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send acknowledgment: {}", e.getMessage(), e);
        }
    }
}
