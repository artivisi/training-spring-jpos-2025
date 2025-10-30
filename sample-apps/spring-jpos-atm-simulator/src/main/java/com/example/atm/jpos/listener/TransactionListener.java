package com.example.atm.jpos.listener;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;

@Slf4j
public class TransactionListener implements ISORequestListener {

    @Override
    public boolean process(ISOSource source, ISOMsg msg) {
        try {
            log.info("Received ISO 8583 message:");
            log.info("  MTI: {}", msg.getMTI());
            log.info("  Field 2: {}", msg.getString(2));
            log.info("  Field 3: {}", msg.getString(3));

            return true;
        } catch (Exception e) {
            log.error("Error processing message", e);
            return false;
        }
    }
}
