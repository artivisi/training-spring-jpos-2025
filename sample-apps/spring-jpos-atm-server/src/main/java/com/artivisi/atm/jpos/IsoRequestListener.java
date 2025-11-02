package com.artivisi.atm.jpos;

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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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
        log.info("IsoRequestListener.process() called - START");
        try {
            log.info("Received ISO message: MTI={} STAN={}",
                     msg.getMTI(), msg.getString(11));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            msg.dump(ps, "  ");
            log.info("Full message dump:\n{}", baos.toString());

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
