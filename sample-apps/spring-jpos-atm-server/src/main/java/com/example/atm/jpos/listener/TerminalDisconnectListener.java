package com.example.atm.jpos.listener;

import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.jpos.service.ChannelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOServerClientDisconnectEvent;
import org.jpos.iso.ISOServerEventListener;

import java.util.EventObject;

/**
 * Listener for terminal disconnection events from QServer.
 * Handles cleanup when terminals disconnect abruptly (network failure, crash, etc.).
 *
 * This listener implements ISOServerEventListener to receive disconnect notifications
 * from jPOS ISOServer, and performs automatic cleanup of the ChannelRegistry.
 *
 * Best practice: Uses channel.getName() for identification since terminal ID
 * is not available in disconnect events.
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 * Uses SpringBeanFactory to access Spring beans.
 */
@Slf4j
public class TerminalDisconnectListener implements ISOServerEventListener {

    private ChannelRegistry getChannelRegistry() {
        return SpringBeanFactory.getBean(ChannelRegistry.class);
    }

    /**
     * Handle ISO server events, specifically disconnect events.
     * Automatically unregisters terminals when they disconnect.
     *
     * @param event The server event (ISOServerClientDisconnectEvent for disconnects)
     */
    @Override
    public void handleISOServerEvent(EventObject event) {
        try {
            if (event instanceof ISOServerClientDisconnectEvent) {
                handleDisconnect((ISOServerClientDisconnectEvent) event);
            }
            // Ignore other event types (ISOServerAcceptEvent, ISOServerShutdownEvent)
        } catch (Exception e) {
            log.error("Error handling disconnect event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle client disconnect event.
     * Performs cleanup by removing terminal from registry.
     */
    private void handleDisconnect(ISOServerClientDisconnectEvent event) {
        ISOChannel channel = event.getISOChannel();
        if (channel == null) {
            log.warn("Disconnect event with null channel");
            return;
        }

        String channelName = channel.getName();
        log.info("Terminal disconnected: channelName={}, connected={}",
                channelName, channel.isConnected());

        // Unregister by channel name (performs reverse lookup to find terminal ID)
        getChannelRegistry().unregisterByChannelName(channelName);
    }
}
