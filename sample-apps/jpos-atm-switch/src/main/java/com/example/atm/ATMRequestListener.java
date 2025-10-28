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

public class ATMRequestListener implements ISORequestListener, LogSource, Configurable {
    private Configuration cfg;
    private Logger logger;
    private String realm;
    private Space sp;
    private String queue;
    private long timeout;

    @Override
    public boolean process(ISOSource source, ISOMsg msg) {
        try {
            Context ctx = new Context();
            ctx.put("REQUEST", msg);
            ctx.put("SOURCE", source);

            sp.out(queue, ctx, timeout);

            return true;
        } catch (Exception e) {
            if (logger != null) {
                LogEvent evt = new LogEvent("atm-request-listener");
                evt.addMessage(e);
                logger.log(evt);
            }
            return false;
        }
    }

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.cfg = cfg;
        String spaceName = cfg.get("space", "tspace:default");
        queue = cfg.get("queue", "txnmgr");
        timeout = cfg.getLong("timeout", 60000);

        try {
            sp = SpaceFactory.getSpace(spaceName);
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
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
}
