package com.example.atm.jpos;

import lombok.extern.slf4j.Slf4j;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.transaction.Context;

@Slf4j
public class IsoRequestListener implements ISORequestListener, Configurable {

    @SuppressWarnings("rawtypes")
    private Space space;
    private String queue;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.space = SpaceFactory.getSpace(cfg.get("space", "tspace:default"));
        this.queue = cfg.get("queue", "txnmgr");
        log.info("IsoRequestListener configured with space={} queue={}",
                 cfg.get("space", "tspace:default"), queue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean process(ISOSource source, ISOMsg msg) {
        try {
            log.info("Received ISO message: MTI={} STAN={}",
                     msg.getMTI(), msg.getString(11));

            Context ctx = new Context();
            ctx.put("SOURCE", source);
            ctx.put("REQUEST", msg);

            space.out(queue, ctx);

            log.debug("Message queued to TransactionManager");
            return true;

        } catch (Exception e) {
            log.error("Error processing ISO message: ", e);
            return false;
        }
    }
}
