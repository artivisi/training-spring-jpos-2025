package com.artivisi.atm.jpos.service;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageValidator {

    public boolean validateRequest(ISOMsg msg) throws ISOException {
        if (!"0200".equals(msg.getMTI())) {
            log.error("Invalid MTI: {}", msg.getMTI());
            return false;
        }

        if (!msg.hasField(2) || msg.getString(2).isEmpty()) {
            log.error("Missing or empty PAN");
            return false;
        }

        if (!msg.hasField(3) || msg.getString(3).isEmpty()) {
            log.error("Missing or empty Processing Code");
            return false;
        }

        if (!msg.hasField(52) || msg.getString(52).isEmpty()) {
            log.error("Missing or empty PIN Block");
            return false;
        }

        log.debug("Message validation passed");
        return true;
    }

    public boolean isApproved(ISOMsg response) throws ISOException {
        String responseCode = response.getString(39);
        return "00".equals(responseCode);
    }

    public String getResponseMessage(String responseCode) {
        return switch (responseCode) {
            case "00" -> "Approved";
            case "14" -> "Invalid card number";
            case "30" -> "Format error";
            case "51" -> "Insufficient funds";
            case "55" -> "Incorrect PIN";
            case "62" -> "Restricted card";
            case "91" -> "System error";
            case "96" -> "System malfunction";
            default -> "Transaction declined";
        };
    }
}
