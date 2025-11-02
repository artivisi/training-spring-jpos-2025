package com.artivisi.atm.jpos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.MUX;
import org.jpos.util.NameRegistrar;
import org.springframework.stereotype.Service;

/**
 * Centralized service for interacting with jPOS MUX.
 * Abstracts the Spring → jPOS integration and provides consistent error handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MuxService {

    private final String muxName;

    /**
     * Get MUX instance from jPOS NameRegistrar.
     *
     * @return MUX instance
     * @throws MuxException if MUX not found or not available
     */
    private MUX getMux() throws MuxException {
        try {
            return (MUX) NameRegistrar.get(muxName);
        } catch (NameRegistrar.NotFoundException e) {
            log.error("MUX not found: {}", muxName);
            throw new MuxException("MUX not available: " + muxName, e);
        }
    }

    /**
     * Check if MUX is connected.
     *
     * @return true if MUX is connected
     */
    public boolean isConnected() {
        try {
            MUX mux = getMux();
            return mux.isConnected();
        } catch (MuxException e) {
            log.debug("Cannot check MUX connection status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Wait for MUX to be ready with timeout.
     *
     * @param timeoutMillis Maximum time to wait in milliseconds
     * @return true if MUX became ready within timeout
     */
    public boolean waitForReady(long timeoutMillis) {
        try {
            MUX mux = getMux();
            if (mux.isConnected()) {
                return true;
            }

            log.info("MUX not connected, waiting for ready...");
            long startWait = System.currentTimeMillis();
            while (!mux.isConnected() && (System.currentTimeMillis() - startWait) < timeoutMillis) {
                Thread.sleep(100);
            }

            boolean ready = mux.isConnected();
            log.info("MUX ready status after wait: connected={}", ready);
            return ready;

        } catch (MuxException e) {
            log.error("Error waiting for MUX: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for MUX");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Send an ISO message and wait for response.
     *
     * @param request ISO message to send
     * @param timeoutMillis Maximum time to wait for response in milliseconds
     * @return ISO response message, or null if timeout/error
     * @throws MuxException if MUX not available or not connected
     */
    public ISOMsg request(ISOMsg request, long timeoutMillis) throws MuxException {
        MUX mux = getMux();

        if (!mux.isConnected()) {
            log.error("MUX not connected, cannot send request");
            throw new MuxException("MUX not connected");
        }

        try {
            log.debug("Sending ISO message via MUX: MTI={}", request.getMTI());
            ISOMsg response = mux.request(request, timeoutMillis);

            if (response == null) {
                log.error("MUX returned null response after timeout");
            }

            return response;

        } catch (Exception e) {
            log.error("Error sending request via MUX: {}", e.getMessage(), e);
            throw new MuxException("Error sending request via MUX", e);
        }
    }

    /**
     * Send an ISO message and wait for response with default timeout.
     *
     * @param request ISO message to send
     * @return ISO response message, or null if timeout/error
     * @throws MuxException if MUX not available or not connected
     */
    public ISOMsg request(ISOMsg request) throws MuxException {
        return request(request, 30000);
    }

    /**
     * Exception thrown when MUX operations fail.
     */
    public static class MuxException extends Exception {
        public MuxException(String message) {
            super(message);
        }

        public MuxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
