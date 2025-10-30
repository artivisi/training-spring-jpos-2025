package com.example.atm.jpos.service;

import com.example.atm.dto.TransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.Base24Packager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ISO8583MessageBuilder {

    private final PINBlockService pinBlockService;
    private final Random random = new Random();

    public ISOMsg buildTransactionRequest(TransactionRequest request, String pinBlock)
        throws ISOException {

        ISOMsg msg = new ISOMsg();
        msg.setPackager(new Base24Packager());
        msg.setMTI("0200");

        msg.set(2, request.getPan());

        String processingCode = getProcessingCode(request.getType());
        msg.set(3, processingCode);

        if (request.getAmount() != null) {
            String amountStr = formatAmount(request.getAmount());
            msg.set(4, amountStr);
        }

        msg.set(7, getCurrentTimestamp());

        msg.set(11, generateSTAN());

        msg.set(41, request.getTerminalId());

        msg.set(52, pinBlock);

        log.info("Built ISO 8583 message:");
        log.info("  MTI: {}", msg.getMTI());
        log.info("  Field 2 (PAN): {}", msg.getString(2));
        log.info("  Field 3 (Processing Code): {}", msg.getString(3));
        log.info("  Field 4 (Amount): {}", msg.getString(4));
        log.info("  Field 7 (Transmission Date/Time): {}", msg.getString(7));
        log.info("  Field 11 (STAN): {}", msg.getString(11));
        log.info("  Field 41 (Terminal ID): {}", msg.getString(41));
        log.info("  Field 52 (PIN Block): {}", msg.getString(52));

        return msg;
    }

    private String getProcessingCode(TransactionRequest.TransactionType type) {
        return switch (type) {
            case BALANCE -> "310000";
            case WITHDRAWAL -> "010000";
        };
    }

    private String formatAmount(BigDecimal amount) {
        long amountCents = amount.multiply(new BigDecimal("100")).longValue();
        return String.format("%012d", amountCents);
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        return sdf.format(new Date());
    }

    private String generateSTAN() {
        return String.format("%06d", random.nextInt(999999));
    }
}
