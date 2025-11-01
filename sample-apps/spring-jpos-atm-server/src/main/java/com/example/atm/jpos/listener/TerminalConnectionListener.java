package com.example.atm.jpos.listener;

import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.jpos.service.ChannelRegistry;
import com.example.atm.jpos.util.TerminalIdUtil;
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
     * IMPORTANT: Returns false to allow the message to continue to next listeners.
     * Returning true would stop the chain and prevent transaction manager processing.
     *
     * @param source The ISO channel that received the message
     * @param m The received ISO message
     * @return false to pass message to next listener (IsoRequestListener)
     */
    @Override
    public boolean process(org.jpos.iso.ISOSource source, ISOMsg m) {
        try {
            String mti = m.getMTI();
            String terminalId = TerminalIdUtil.extractTerminalId(m);

            if (terminalId == null || terminalId.trim().isEmpty()) {
                log.warn("Cannot process message: missing terminal ID");
                return false; // Pass to next listener
            }

            // Get the ISO channel from the source
            if (!(source instanceof ISOChannel)) {
                log.warn("Source is not an ISOChannel, cannot register");
                return false; // Pass to next listener
            }

            ISOChannel channel = (ISOChannel) source;

            // Check if this is a sign-on message (0800 with field 70 = "001")
            if ("0800".equals(mti)) {
                String networkMgmtCode = m.getString(70);

                if ("001".equals(networkMgmtCode)) {
                    // Sign-on message - register the terminal
                    handleSignOn(terminalId, channel);
                    log.info("Sign-on registration complete, passing to transaction manager");
                    return false; // Pass to IsoRequestListener for response generation
                } else if ("002".equals(networkMgmtCode)) {
                    // Sign-off message - unregister the terminal
                    handleSignOff(terminalId);
                    log.info("Sign-off registration complete, passing to transaction manager");
                    return false; // Pass to IsoRequestListener for response generation
                }
            }

            // For all other messages, verify terminal is signed on
            if (!getChannelRegistry().isSignedOn(terminalId)) {
                log.error("Terminal not signed on, rejecting request: terminalId={}, MTI={}",
                        terminalId, mti);
                // Still pass to transaction manager - SignOnValidationParticipant will reject it
            }

        } catch (Exception e) {
            log.warn("Error in connection listener: {}", e.getMessage());
        }

        // Return false to pass message to next listener (IsoRequestListener)
        return false;
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
}
