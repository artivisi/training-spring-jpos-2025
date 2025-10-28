package com.example.atm;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.q2.QBeanSupport;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.transaction.Context;
import org.jpos.util.LogEvent;

public class IncomingListener extends QBeanSupport implements Runnable, Configurable {
    private String in;
    private String spaceName;
    private String queue;
    private Space sp;
    private Thread thread;

    @Override
    protected void startService() {
        try {
            sp = SpaceFactory.getSpace(spaceName);
            thread = new Thread(this, getName());
            thread.start();
            LogEvent evt = new LogEvent("incoming-listener");
            evt.addMessage("IncomingListener started, queue: " + in);
            getLog().info(evt);
        } catch (Exception e) {
            getLog().error("Failed to start incoming listener", e);
        }
    }

    @Override
    protected void stopService() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running()) {
            try {
                Object obj = sp.in(in, 1000);
                if (obj != null) {
                    LogEvent evt = new LogEvent("incoming-listener");
                    evt.addMessage("Received object from queue: " + obj.getClass().getName());
                    getLog().info(evt);

                    ISOMsg msg = null;
                    ISOSource source = null;

                    if (obj instanceof ISOMsg) {
                        msg = (ISOMsg) obj;
                        source = (ISOSource) sp.in(in, 1000);
                    } else if (obj instanceof Object[]) {
                        Object[] arr = (Object[]) obj;
                        if (arr.length >= 2) {
                            msg = (ISOMsg) arr[0];
                            source = (ISOSource) arr[1];
                        }
                    }

                    if (msg != null && source != null) {
                        Context ctx = new Context();
                        ctx.put("REQUEST", msg);
                        ctx.put("SOURCE", source);

                        LogEvent evt2 = new LogEvent("incoming-listener");
                        evt2.addMessage("Forwarding to TxnMgr: " + msg.getMTI());
                        getLog().info(evt2);

                        sp.out(queue, ctx, 30000);
                    } else {
                        LogEvent evt3 = new LogEvent("incoming-listener");
                        evt3.addMessage("msg or source is null");
                        getLog().error(evt3);
                    }
                }
            } catch (Exception e) {
                if (running()) {
                    LogEvent evt = new LogEvent("incoming-listener");
                    evt.addMessage(e);
                    getLog().error(evt);
                }
            }
        }
    }

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        super.setConfiguration(cfg);
        in = cfg.get("in", "receive");
        spaceName = cfg.get("space", "tspace:default");
        queue = cfg.get("queue", "txnmgr");
    }
}
