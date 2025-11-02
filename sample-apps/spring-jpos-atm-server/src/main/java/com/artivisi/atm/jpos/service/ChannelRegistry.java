package com.artivisi.atm.jpos.service;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOChannel;
import org.springframework.stereotype.Service;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking connected ATM terminals and their ISO channels.
 * Uses WeakReference to prevent memory leaks when channels disconnect.
 * Thread-safe for concurrent access.
 *
 * Manages terminal sign-on state - terminals must sign on before transacting.
 */
@Service
@Slf4j
public class ChannelRegistry {

    // Map: terminalId -> WeakReference<ISOChannel>
    private final Map<String, WeakReference<ISOChannel>> channels = new ConcurrentHashMap<>();

    // Reverse map: channelName -> terminalId (for disconnect event lookup)
    private final Map<String, String> channelNameToTerminalId = new ConcurrentHashMap<>();

    // Set of signed-on terminal IDs
    private final Set<String> signedOnTerminals = ConcurrentHashMap.newKeySet();

    /**
     * Register a connected terminal's ISO channel.
     *
     * @param terminalId Terminal identifier (e.g., "TRM-ISS001-ATM-001")
     * @param channel ISO channel for communication
     */
    public void register(String terminalId, ISOChannel channel) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            log.warn("Cannot register channel with null or empty terminal ID");
            return;
        }

        if (channel == null) {
            log.warn("Cannot register null channel for terminal: {}", terminalId);
            return;
        }

        channels.put(terminalId, new WeakReference<>(channel));
        channelNameToTerminalId.put(channel.getName(), terminalId);
        log.info("Registered channel for terminal: {}, channelName={}, connected={}",
                terminalId, channel.getName(), channel.isConnected());
    }

    /**
     * Mark a terminal as signed on.
     * Should be called after successful sign-on message processing.
     *
     * @param terminalId Terminal identifier
     */
    public void signOn(String terminalId) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            return;
        }

        signedOnTerminals.add(terminalId);
        log.info("Terminal signed on: {}", terminalId);
    }

    /**
     * Mark a terminal as signed off.
     * Should be called on sign-off message or disconnect.
     *
     * @param terminalId Terminal identifier
     */
    public void signOff(String terminalId) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            return;
        }

        signedOnTerminals.remove(terminalId);
        log.info("Terminal signed off: {}", terminalId);
    }

    /**
     * Check if a terminal is signed on.
     *
     * @param terminalId Terminal identifier
     * @return true if terminal has completed sign-on
     */
    public boolean isSignedOn(String terminalId) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            return false;
        }
        return signedOnTerminals.contains(terminalId);
    }

    /**
     * Unregister a terminal's channel (typically on disconnect).
     *
     * @param terminalId Terminal identifier
     */
    public void unregister(String terminalId) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            return;
        }

        WeakReference<ISOChannel> ref = channels.remove(terminalId);
        signedOnTerminals.remove(terminalId);  // Also remove from signed-on set

        // Remove from reverse map
        if (ref != null) {
            ISOChannel channel = ref.get();
            if (channel != null) {
                channelNameToTerminalId.remove(channel.getName());
            }
            log.info("Unregistered channel for terminal: {}", terminalId);
        }
    }

    /**
     * Unregister a terminal by channel name (used by disconnect event handler).
     * This is called when we receive a disconnect event and need to lookup the terminal.
     *
     * @param channelName The channel name from ISOChannel.getName()
     */
    public void unregisterByChannelName(String channelName) {
        if (channelName == null || channelName.trim().isEmpty()) {
            return;
        }

        String terminalId = channelNameToTerminalId.remove(channelName);
        if (terminalId != null) {
            channels.remove(terminalId);
            signedOnTerminals.remove(terminalId);
            log.info("Unregistered terminal by channel name: terminalId={}, channelName={}",
                    terminalId, channelName);
        } else {
            log.debug("No terminal found for channel name: {}", channelName);
        }
    }

    /**
     * Get the ISO channel for a specific terminal.
     *
     * @param terminalId Terminal identifier
     * @return ISOChannel if available and connected, null otherwise
     */
    public ISOChannel getChannel(String terminalId) {
        if (terminalId == null || terminalId.trim().isEmpty()) {
            return null;
        }

        WeakReference<ISOChannel> ref = channels.get(terminalId);
        if (ref == null) {
            log.debug("No channel registered for terminal: {}", terminalId);
            return null;
        }

        ISOChannel channel = ref.get();
        if (channel == null) {
            // WeakReference was garbage collected
            log.debug("Channel was garbage collected for terminal: {}", terminalId);
            channels.remove(terminalId);
            return null;
        }

        if (!channel.isConnected()) {
            log.debug("Channel disconnected for terminal: {}", terminalId);
            channels.remove(terminalId);
            return null;
        }

        return channel;
    }

    /**
     * Check if a terminal is currently connected.
     *
     * @param terminalId Terminal identifier
     * @return true if terminal has an active connection
     */
    public boolean isConnected(String terminalId) {
        return getChannel(terminalId) != null;
    }

    /**
     * Get all currently connected terminal IDs.
     *
     * @return Set of terminal IDs with active connections
     */
    public Set<String> getConnectedTerminals() {
        // Clean up disconnected channels
        channels.entrySet().removeIf(entry -> {
            WeakReference<ISOChannel> ref = entry.getValue();
            ISOChannel channel = ref.get();
            return channel == null || !channel.isConnected();
        });

        return Set.copyOf(channels.keySet());
    }

    /**
     * Get count of currently connected terminals.
     *
     * @return Number of active connections
     */
    public int getConnectedCount() {
        return getConnectedTerminals().size();
    }

    /**
     * Clear all registered channels (for shutdown/testing).
     */
    public void clear() {
        int count = channels.size();
        channels.clear();
        channelNameToTerminalId.clear();
        signedOnTerminals.clear();
        log.info("Cleared {} channel registrations", count);
    }
}
