package com.artivisi.atm.jpos.service;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PINBlockService {

    public String calculatePINBlock(String pin, String pan) {
        log.info("Calculating PIN block for PAN: {}...", pan.substring(0, 6));

        String pinBlock = formatPIN(pin);
        String panBlock = extractPANBlock(pan);
        String result = xorBlocks(pinBlock, panBlock);

        log.info("PIN Block calculation:");
        log.info("  PIN Block (formatted): {}", pinBlock);
        log.info("  PAN Block: {}", panBlock);
        log.info("  Result (XOR): {}", result);

        return result;
    }

    private String formatPIN(String pin) {
        String pinLength = String.format("%02d", pin.length());
        String paddedPin = pin + "F".repeat(14 - pin.length());
        return "0" + pinLength + paddedPin;
    }

    private String extractPANBlock(String pan) {
        String pan12 = pan.substring(pan.length() - 13, pan.length() - 1);
        return "0000" + pan12;
    }

    private String xorBlocks(String block1, String block2) {
        byte[] bytes1 = ISOUtil.hex2byte(block1);
        byte[] bytes2 = ISOUtil.hex2byte(block2);
        byte[] result = new byte[8];

        for (int i = 0; i < 8; i++) {
            result[i] = (byte) (bytes1[i] ^ bytes2[i]);
        }

        return ISOUtil.hexString(result);
    }

    public String encryptPINBlock(String pinBlock, String tpkValue) {
        log.info("Encrypting PIN block with TPK");
        return pinBlock;
    }
}
