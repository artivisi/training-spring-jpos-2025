package com.example.atm;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.transaction.Context;
import org.jpos.util.LogEvent;
import org.jpos.util.LogSource;
import org.jpos.util.Logger;

/**
 * Request listener that checks throttle state before forwarding to Space.
 * When throttled, accepts connections but returns ISO-8583 response code 91 (System error/unavailable).
 *
 * Configuration properties:
 * - space: Space name (default: tspace:default)
 * - queue: Queue name to forward requests (required)
 * - timeout: Timeout for space.out operation in ms (default: 60000)
 * - throttle-response-code: ISO-8583 response code when throttled (default: 91)
 */
public class ThrottleAwareRequestListener implements ISORequestListener, LogSource, Configurable {

    private Space sp;
    private String queue;
    private long timeout;
    private String throttleResponseCode = "91";
    private Logger logger;
    private String realm;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        String spaceName = cfg.get("space", "tspace:default");
        this.sp = SpaceFactory.getSpace(spaceName);

        this.queue = cfg.get("queue");
        if (queue == null || queue.trim().isEmpty()) {
            throw new ConfigurationException("queue property is required");
        }

        this.timeout = cfg.getLong("timeout", 60000);
        this.throttleResponseCode = cfg.get("throttle-response-code", "91");
    }

    @Override
    public void setLogger(Logger logger, String realm) {
        this.logger = logger;
        this.realm = realm;
    }

    @Override
    public String getRealm() {
        return realm;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public boolean process(ISOSource source, ISOMsg msg) {
        // Check if system is throttled
        if (ThrottleManager.isThrottled()) {
            try {
                sendThrottleResponse(source, msg);
                logEvent("Request rejected (system throttled): MTI=" + msg.getMTI(), null);
                return true; // Return true to indicate we handled the request
            } catch (Exception e) {
                logEvent("Error sending throttle response", e);
                return false;
            }
        }

        // Normal processing - forward to Space
        try {
            Context ctx = new Context();
            ctx.put("REQUEST", msg);
            ctx.put("SOURCE", source);
            sp.out(queue, ctx, timeout);
            return true;
        } catch (Exception e) {
            logEvent("Error forwarding request to queue: " + queue, e);
            return false;
        }
    }

    private void sendThrottleResponse(ISOSource source, ISOMsg request) throws Exception {
        ISOMsg response = (ISOMsg) request.clone();

        // Convert request MTI to response MTI
        String mti = request.getMTI();
        if (mti != null) {
            if (mti.startsWith("02")) {
                response.setMTI(mti.substring(0, 2) + "1" + mti.substring(3)); // 0200 -> 0210
            } else if (mti.startsWith("04")) {
                response.setMTI(mti.substring(0, 2) + "1" + mti.substring(3)); // 0400 -> 0410
            }
        }

        // Set response code indicating system unavailable
        response.set(39, throttleResponseCode);

        // Send response back to client
        source.send(response);
    }

    private void logEvent(String message, Exception e) {
        if (logger != null) {
            LogEvent ev = new LogEvent("throttle-aware-listener");
            ev.addMessage(message);
            if (e != null) {
                ev.addMessage(e);
            }
            logger.log(ev);
        }
    }
}
