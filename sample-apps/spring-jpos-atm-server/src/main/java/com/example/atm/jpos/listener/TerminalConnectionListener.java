package com.example.atm.jpos.listener;

import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.jpos.service.ChannelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOMsg;

/**
 * Listener for terminal connections to QServer.
 * Handles sign-on messages and registers ISO channels in the ChannelRegistry.
 *
 * Sign-on is mandatory: MTI 0800 with field 70 = "001"
 * Terminals must sign on before performing any transactions.
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 * Uses SpringBeanFactory to access Spring beans.
 */
@Slf4j
public class TerminalConnectionListener implements org.jpos.iso.ISORequestListener {

    private ChannelRegistry getChannelRegistry() {
        return SpringBeanFactory.getBean(ChannelRegistry.class);
    }

    /**
     * Called when a message is received from a connected terminal.
     * Handles sign-on messages specially to register terminals.
     *
     * @param source The ISO channel that received the message
     * @param m The received ISO message
     * @return true to allow normal processing, false to stop
     */
    @Override
    public boolean process(org.jpos.iso.ISOSource source, ISOMsg m) {
        try {
            String mti = m.getMTI();
            String terminalId = extractTerminalId(m);

            if (terminalId == null || terminalId.trim().isEmpty()) {
                log.warn("Cannot process message: missing terminal ID");
                return true; // Let normal processing handle the error
            }

            // Get the ISO channel from the source
            if (!(source instanceof ISOChannel)) {
                log.warn("Source is not an ISOChannel, cannot register");
                return true;
            }

            ISOChannel channel = (ISOChannel) source;

            // Check if this is a sign-on message (0800 with field 70 = "001")
            if ("0800".equals(mti)) {
                String networkMgmtCode = m.getString(70);

                if ("001".equals(networkMgmtCode)) {
                    // Sign-on message
                    handleSignOn(terminalId, channel);
                    return true; // Let normal processing send 0810 response
                } else if ("002".equals(networkMgmtCode)) {
                    // Sign-off message
                    handleSignOff(terminalId);
                    return true;
                }
            }

            // For all other messages, verify terminal is signed on
            if (!getChannelRegistry().isSignedOn(terminalId)) {
                log.error("Terminal not signed on, rejecting request: terminalId={}, MTI={}",
                        terminalId, mti);
                // Return true but transaction participants will reject it
            }

        } catch (Exception e) {
            log.warn("Error in connection listener: {}", e.getMessage());
        }

        // Return true to allow normal processing to continue
        return true;
    }

    /**
     * Handle sign-on message from terminal.
     * Registers the channel and marks terminal as signed on.
     */
    private void handleSignOn(String terminalId, ISOChannel channel) {
        log.info("Processing sign-on: terminalId={}, channel={}", terminalId, channel.getName());

        // Register the channel
        getChannelRegistry().register(terminalId, channel);

        // Mark as signed on
        getChannelRegistry().signOn(terminalId);

        log.info("Terminal signed on successfully: {}", terminalId);
    }

    /**
     * Handle sign-off message from terminal.
     * Marks terminal as signed off (but keeps channel registered).
     */
    private void handleSignOff(String terminalId) {
        log.info("Processing sign-off: terminalId={}", terminalId);
        getChannelRegistry().signOff(terminalId);
        log.info("Terminal signed off: {}", terminalId);
    }

    /**
     * Extract terminal ID from ISO message fields.
     * Combines field 42 (Card Acceptor ID) with field 41 (Terminal ID).
     *
     * @param m ISO message
     * @return Terminal ID in format "INSTITUTION-TERMINAL" or null if not found
     */
    private String extractTerminalId(ISOMsg m) {
        try {
            String cardAcceptorId = m.getString(42);  // e.g., "TRM-ISS001"
            String terminalId = m.getString(41);       // e.g., "ATM-001"

            if (terminalId == null || terminalId.trim().isEmpty()) {
                return null;
            }

            // Combine both fields if card acceptor ID is present
            if (cardAcceptorId != null && !cardAcceptorId.trim().isEmpty()) {
                return cardAcceptorId.trim() + "-" + terminalId.trim();
            }

            return terminalId.trim();

        } catch (Exception e) {
            log.debug("Could not extract terminal ID from message: {}", e.getMessage());
            return null;
        }
    }
}
