package com.example.atm;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.q2.QBeanSupport;
import org.jpos.space.LocalSpace;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;

/**
 * QBean that monitors the Space queue size and sets throttle flag
 * when the queue size reaches a configured threshold.
 * Works with ThrottleAwareRequestListener to gracefully reject requests
 * with ISO-8583 response code instead of refusing TCP connections.
 *
 * Configuration properties:
 * - space: Space name (default: tspace:default)
 * - queue: Queue name to monitor (required)
 * - check-interval: Monitoring interval in milliseconds (default: 1000)
 * - high-threshold: Queue size to trigger throttling (default: 100)
 * - low-threshold: Queue size to resume normal operation (default: 50)
 */
public class QueueMonitor extends QBeanSupport implements Runnable, Configurable {

    private LocalSpace space;
    private String queueName;
    private long checkInterval = 1000;
    private int highThreshold = 100;
    private int lowThreshold = 50;
    private volatile boolean running = false;
    private Thread monitorThread;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        String spaceName = cfg.get("space", "tspace:default");
        Space sp = SpaceFactory.getSpace(spaceName);
        if (!(sp instanceof LocalSpace)) {
            throw new ConfigurationException("Space must implement LocalSpace interface to support size() method");
        }
        this.space = (LocalSpace) sp;

        this.queueName = cfg.get("queue");
        if (queueName == null || queueName.trim().isEmpty()) {
            throw new ConfigurationException("queue property is required");
        }

        this.checkInterval = cfg.getLong("check-interval", 1000);
        this.highThreshold = cfg.getInt("high-threshold", 100);
        this.lowThreshold = cfg.getInt("low-threshold", 50);

        if (lowThreshold >= highThreshold) {
            throw new ConfigurationException(
                "low-threshold (" + lowThreshold + ") must be less than high-threshold (" + highThreshold + ")"
            );
        }

        log.info("QueueMonitor configured:");
        log.info("  Queue: " + queueName);
        log.info("  Check interval: " + checkInterval + "ms");
        log.info("  High threshold: " + highThreshold);
        log.info("  Low threshold: " + lowThreshold);
    }

    @Override
    protected void startService() {
        running = true;
        monitorThread = new Thread(this, "QueueMonitor-" + queueName);
        monitorThread.start();
        log.info("QueueMonitor started for queue: " + queueName);
    }

    @Override
    protected void stopService() {
        running = false;
        if (monitorThread != null) {
            try {
                monitorThread.join(5000);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for monitor thread to stop", e);
                Thread.currentThread().interrupt();
            }
        }

        // Clear throttle flag
        ThrottleManager.setThrottled(false);

        log.info("QueueMonitor stopped for queue: " + queueName);
    }

    @Override
    public void run() {
        while (running) {
            try {
                int queueSize = space.size(queueName);
                boolean currentlyThrottled = ThrottleManager.isThrottled();

                if (!currentlyThrottled && queueSize >= highThreshold) {
                    log.warn("Queue size (" + queueSize + ") reached high threshold (" + highThreshold + "). Enabling throttle mode.");
                    ThrottleManager.setThrottled(true);
                } else if (currentlyThrottled && queueSize <= lowThreshold) {
                    log.info("Queue size (" + queueSize + ") dropped to low threshold (" + lowThreshold + "). Disabling throttle mode.");
                    ThrottleManager.setThrottled(false);
                } else if (currentlyThrottled) {
                    log.debug("Queue size: " + queueSize + " (throttled, waiting for size <= " + lowThreshold + ")");
                } else {
                    log.debug("Queue size: " + queueSize + " (normal operation)");
                }

                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                log.info("QueueMonitor interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error monitoring queue: " + queueName, e);
                try {
                    Thread.sleep(checkInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
