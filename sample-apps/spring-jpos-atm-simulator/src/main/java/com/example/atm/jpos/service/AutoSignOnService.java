package com.example.atm.jpos.service;

import com.example.atm.jpos.config.SpringBeanFactory;
import lombok.extern.slf4j.Slf4j;
import org.jpos.q2.QBeanSupport;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;

/**
 * Q2 service that automatically sends sign-on message when channel becomes ready.
 * Monitors the channel ready event and triggers SignOnService.
 *
 * This class is managed by jPOS Q2 and uses SpringBeanFactory to access Spring beans.
 */
@Slf4j
public class AutoSignOnService extends QBeanSupport implements Runnable {

    private Space space;
    private String readyIndicator;
    private volatile boolean running = true;
    private Thread monitorThread;

    @Override
    protected void initService() throws Exception {
        log.info("Initializing AutoSignOnService");

        // Get space and ready indicator from configuration
        String spaceName = cfg.get("space", "tspace:default");
        readyIndicator = cfg.get("ready", "atm-channel.ready");

        space = SpaceFactory.getSpace(spaceName);
        log.info("Monitoring channel ready indicator: {}", readyIndicator);
    }

    @Override
    protected void startService() throws Exception {
        log.info("Starting AutoSignOnService");
        monitorThread = new Thread(this, "auto-signon-monitor");
        monitorThread.start();
    }

    @Override
    protected void stopService() throws Exception {
        log.info("Stopping AutoSignOnService");
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread.join(5000);
        }
    }

    @Override
    public void run() {
        log.info("AutoSignOnService monitor thread started");

        while (running) {
            try {
                // Wait for channel to become ready
                // This will block until the ready indicator is present in the space
                Object ready = space.in(readyIndicator, 30000);

                if (ready != null && running) {
                    log.info("Channel ready detected, waiting for MUX connection");

                    // Put the ready indicator back immediately
                    space.out(readyIndicator, ready);

                    // Check if already signed on
                    SignOnService signOnService = SpringBeanFactory.getBean(SignOnService.class);
                    if (signOnService.isSignedOn()) {
                        log.debug("Already signed on, skipping automatic sign-on");
                        continue;
                    }

                    // Wait for MUX to be fully connected (max 10 seconds)
                    MuxService muxService = SpringBeanFactory.getBean(MuxService.class);
                    boolean muxReady = waitForMuxConnection(muxService, 10000);

                    if (!muxReady) {
                        log.error("MUX not connected after 10 seconds, cannot sign on");
                        continue;
                    }

                    log.info("MUX connected, initiating automatic sign-on");

                    // Trigger sign-on via Spring service
                    try {
                        boolean success = signOnService.signOn();

                        if (success) {
                            log.info("Automatic sign-on completed successfully");
                            // Sign-on successful, exit the loop
                            log.info("AutoSignOnService completed, terminating monitor thread");
                            break;
                        } else {
                            log.error("Automatic sign-on failed, will retry on next channel ready event");
                        }
                    } catch (Exception e) {
                        log.error("Error during automatic sign-on: {}", e.getMessage(), e);
                    }
                }

            } catch (Exception e) {
                log.error("Error in AutoSignOnService monitor: {}", e.getMessage(), e);
                // Brief pause before retrying
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    log.warn("Monitor thread interrupted during error recovery");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("AutoSignOnService monitor thread stopped");
    }

    /**
     * Wait for MUX to be connected with timeout.
     *
     * @param muxService MuxService to check
     * @param timeoutMillis Maximum time to wait in milliseconds
     * @return true if MUX connected within timeout
     */
    private boolean waitForMuxConnection(MuxService muxService, long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        int attemptCount = 0;

        while ((System.currentTimeMillis() - startTime) < timeoutMillis) {
            attemptCount++;

            if (muxService.isConnected()) {
                log.info("MUX connected after {} ms (attempt {})",
                    System.currentTimeMillis() - startTime, attemptCount);
                return true;
            }

            try {
                // Wait 500ms between checks
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for MUX connection");
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.warn("MUX not connected after {} ms ({} attempts)", timeoutMillis, attemptCount);
        return false;
    }
}
