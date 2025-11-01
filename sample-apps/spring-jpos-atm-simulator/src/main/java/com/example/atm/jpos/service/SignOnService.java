package com.example.atm.jpos.service;

import com.example.atm.util.TerminalIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for handling terminal sign-on/sign-off with server.
 * Sign-on is mandatory before performing any financial transactions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignOnService {

    private final MuxService muxService;

    @Value("${terminal.id}")
    private String terminalId;

    @Value("${terminal.institution.id}")
    private String institutionId;

    private volatile boolean signedOn = false;

    /**
     * Send sign-on message to server (0800 with field 70 = "001").
     * This must be called after connecting and before performing transactions.
     *
     * @return true if sign-on successful, false otherwise
     */
    public boolean signOn() {
        log.info("Initiating sign-on: terminalId={}", terminalId);

        try {
            // Build sign-on message
            ISOMsg signOnRequest = buildSignOnMessage();

            // Send to server via MuxService
            ISOMsg response = muxService.request(signOnRequest);

            if (response == null) {
                log.error("Sign-on timeout: no response from server");
                signedOn = false;
                return false;
            }

            // Check response code
            String responseCode = response.getString(39);
            if ("00".equals(responseCode)) {
                log.info("Sign-on successful: terminalId={}", terminalId);
                signedOn = true;
                return true;
            } else {
                log.error("Sign-on failed: responseCode={}", responseCode);
                signedOn = false;
                return false;
            }

        } catch (Exception e) {
            log.error("Sign-on error: {}", e.getMessage(), e);
            signedOn = false;
            return false;
        }
    }

    /**
     * Send sign-off message to server (0800 with field 70 = "002").
     *
     * @return true if sign-off successful, false otherwise
     */
    public boolean signOff() {
        log.info("Initiating sign-off: terminalId={}", terminalId);

        try {
            // Build sign-off message
            ISOMsg signOffRequest = buildSignOffMessage();

            // Send to server via MuxService
            ISOMsg response = muxService.request(signOffRequest);

            if (response == null) {
                log.warn("Sign-off timeout: no response from server");
                signedOn = false;
                return false;
            }

            // Check response code
            String responseCode = response.getString(39);
            if ("00".equals(responseCode)) {
                log.info("Sign-off successful: terminalId={}", terminalId);
                signedOn = false;
                return true;
            } else {
                log.error("Sign-off failed: responseCode={}", responseCode);
                return false;
            }

        } catch (Exception e) {
            log.error("Sign-off error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if terminal is currently signed on.
     *
     * @return true if signed on
     */
    public boolean isSignedOn() {
        return signedOn;
    }

    /**
     * Get configured terminal ID.
     *
     * @return Terminal ID
     */
    public String getTerminalId() {
        return terminalId;
    }

    /**
     * Get configured institution ID.
     *
     * @return Institution ID
     */
    public String getInstitutionId() {
        return institutionId;
    }

    /**
     * Build sign-on message (0800 with field 70 = "001").
     */
    private ISOMsg buildSignOnMessage() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0800");

        // Field 11: STAN
        String stan = String.format("%06d", System.currentTimeMillis() % 1000000);
        msg.set(11, stan);

        // Set terminal identification fields using utility
        TerminalIdUtil.setTerminalIdFields(msg, terminalId, institutionId);

        // Field 70: Network Management Information Code
        // 001 = Sign-on
        msg.set(70, "001");

        log.debug("Built sign-on message: MTI={}, STAN={}, Field70={}, terminalId={}",
                msg.getMTI(), stan, msg.getString(70), terminalId);

        return msg;
    }

    /**
     * Build sign-off message (0800 with field 70 = "002").
     */
    private ISOMsg buildSignOffMessage() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0800");

        // Field 11: STAN
        String stan = String.format("%06d", System.currentTimeMillis() % 1000000);
        msg.set(11, stan);

        // Set terminal identification fields using utility
        TerminalIdUtil.setTerminalIdFields(msg, terminalId, institutionId);

        // Field 70: Network Management Information Code
        // 002 = Sign-off
        msg.set(70, "002");

        log.debug("Built sign-off message: MTI={}, STAN={}, Field70={}",
                msg.getMTI(), stan, msg.getString(70));

        return msg;
    }
}
