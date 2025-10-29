package com.muhardin.endy.tutorial.jpos.q2.listener;

import java.io.IOException;
import org.jpos.iso.*;
import org.jpos.util.*;

public class NetmanRequestListener extends Log implements ISORequestListener {
    @Override
    public boolean process(ISOSource source, ISOMsg m) {
        try {
            if ("2800".equals(m.getMTI())) {
                ISOMsg r = (ISOMsg) m.clone();
                r.setResponseMTI();
                r.set(39, "0000");
                source.send(r);
                return true;
            }
        } catch (ISOException | IOException e) {
            warn(e);
        }
        return false;
    }
}
