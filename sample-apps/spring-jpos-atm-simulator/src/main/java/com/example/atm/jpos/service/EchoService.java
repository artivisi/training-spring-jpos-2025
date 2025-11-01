package com.example.atm.jpos.service;

import com.example.atm.jpos.config.SpringBeanFactory;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.q2.QBeanSupport;
import org.springframework.core.env.Environment;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Q2 service that sends periodic echo/heartbeat messages (0800/301) to keep connection alive.
 *
 * According to jPOS best practices:
 * - Send echo message every 3 minutes during idle periods
 * - This prevents socket timeout and detects connection problems proactively
 * - Server can detect if terminal is still alive
 *
 * Based on jPOS community recommendations:
 * "Since the MUX pool forces an echo on each channel every three minutes,
 *  not getting 'receive' activity in five minutes raises the possibility of a 'hung' line."
 */
@Slf4j
public class EchoService extends QBeanSupport implements Runnable {

    private volatile boolean running = true;
    private Thread echoThread;
    private long echoInterval = 180000; // Default: 3 minutes

    @Override
    protected void initService() throws Exception {
        log.info("Initializing EchoService");

        // Get echo interval from configuration (in milliseconds)
        String intervalStr = cfg.get("interval", "180000");
        try {
            echoInterval = Long.parseLong(intervalStr);
            log.info("Echo interval set to {} ms ({} minutes)", echoInterval, echoInterval / 60000);
        } catch (NumberFormatException e) {
            log.warn("Invalid echo interval '{}', using default 3 minutes", intervalStr);
        }

        if (echoInterval < 30000) {
            log.warn("Echo interval {} ms is too short, setting to minimum 30 seconds", echoInterval);
            echoInterval = 30000;
        }
    }

    @Override
    protected void startService() throws Exception {
        log.info("Starting EchoService");
        echoThread = new Thread(this, "echo-service");
        echoThread.start();
    }

    @Override
    protected void stopService() throws Exception {
        log.info("Stopping EchoService");
        running = false;
        if (echoThread != null) {
            echoThread.interrupt();
            echoThread.join(5000);
        }
    }

    @Override
    public void run() {
        log.info("EchoService thread started - will send heartbeat every {} ms", echoInterval);

        // Wait 30 seconds before starting echo messages (allow time for sign-on)
        try {
            log.info("Waiting 30 seconds before sending first echo...");
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            log.warn("Echo service interrupted during initial delay");
            Thread.currentThread().interrupt();
            return;
        }

        while (running) {
            try {
                MuxService muxService = SpringBeanFactory.getBean(MuxService.class);
                SignOnService signOnService = SpringBeanFactory.getBean(SignOnService.class);

                // Only send echo if connected and signed on
                if (!muxService.isConnected()) {
                    log.debug("MUX not connected, skipping echo");
                    Thread.sleep(10000); // Check again in 10 seconds
                    continue;
                }

                if (!signOnService.isSignedOn()) {
                    log.debug("Not signed on yet, skipping echo");
                    Thread.sleep(10000); // Check again in 10 seconds
                    continue;
                }

                // Send echo message
                log.debug("Sending echo/heartbeat message (0800/301)");
                ISOMsg echoRequest = buildEchoMessage();

                try {
                    ISOMsg echoResponse = muxService.request(echoRequest, 10000);

                    if (echoResponse != null) {
                        String responseCode = echoResponse.getString(39);
                        if ("00".equals(responseCode)) {
                            log.debug("Echo successful - connection alive");
                        } else {
                            log.warn("Echo response code: {} - server may have issues", responseCode);
                        }
                    } else {
                        log.error("Echo timeout - connection may be dead");
                    }
                } catch (MuxService.MuxException e) {
                    log.error("Echo failed: {} - connection problems detected", e.getMessage());
                }

                // Wait for next echo interval
                Thread.sleep(echoInterval);

            } catch (InterruptedException e) {
                log.warn("Echo service interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in echo service: {}", e.getMessage(), e);
                // Brief pause before retrying
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ie) {
                    log.warn("Echo service interrupted during error recovery");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("EchoService thread stopped");
    }

    /**
     * Build echo/heartbeat message (0800 MTI, processing code 301).
     *
     * ISO-8583 Echo Message Format:
     * - MTI: 0800 (Network Management Request)
     * - Processing Code (field 3): 301 (Echo test)
     * - Other fields as required by server
     */
    private ISOMsg buildEchoMessage() throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0800");

        // Field 3: Processing Code - 301 for echo test
        msg.set(3, "301000");

        // Field 7: Transmission Date/Time
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        msg.set(7, sdf.format(new Date()));

        // Field 11: System Trace Audit Number
        msg.set(11, String.format("%06d", System.currentTimeMillis() % 1000000));

        // Field 12: Time, Local Transaction
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        msg.set(12, timeFormat.format(new Date()));

        // Field 13: Date, Local Transaction
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMdd");
        msg.set(13, dateFormat.format(new Date()));

        // Field 41: Terminal ID - get from Spring configuration
        Environment env = SpringBeanFactory.getBean(Environment.class);
        String terminalId = env.getProperty("terminal.id", "ATM-001");
        msg.set(41, String.format("%-15s", terminalId)); // Field 41 is 15 chars, left-aligned

        // Field 70: Network Management Information Code (301 = Echo test)
        msg.set(70, "301");

        log.debug("Built echo message: MTI={}, PC={}, Terminal={}",
                msg.getMTI(), msg.getString(3), terminalId);

        return msg;
    }
}
