package com.artivisi.atm.service;

import com.artivisi.atm.dto.TransactionRequest;
import com.artivisi.atm.dto.TransactionResponse;
import com.artivisi.atm.exception.TransactionException;
import com.artivisi.atm.jpos.service.ISO8583MessageBuilder;
import com.artivisi.atm.jpos.service.MessageValidator;
import com.artivisi.atm.jpos.service.MuxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service for handling ATM transaction business logic.
 * Coordinates ISO message building, sending, validation, and response processing.
 * Separates transaction processing logic from HTTP layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtmTransactionService {

    private final ISO8583MessageBuilder messageBuilder;
    private final MessageValidator messageValidator;
    private final MuxService muxService;

    /**
     * Execute an ATM transaction (balance inquiry or withdrawal).
     *
     * @param request Transaction request details
     * @return Transaction response with result and balance
     * @throws TransactionException if transaction fails
     */
    public TransactionResponse executeTransaction(TransactionRequest request) {
        try {
            log.info("Processing transaction: type={}, pan={}, amount={}",
                    request.getType(), maskPan(request.getPan()), request.getAmount());

            // Build ISO-8583 request message
            ISOMsg isoRequest = messageBuilder.buildTransactionRequest(request, request.getAccountNumber());

            // Send via MUX and get response
            ISOMsg isoResponse = sendRequest(isoRequest);

            // Verify MAC on response
            verifyResponseMac(isoResponse);

            // Parse response
            return parseResponse(isoResponse, request);

        } catch (ISOException e) {
            log.error("ISO message error: {}", e.getMessage(), e);
            throw new TransactionException("ISO message error", e);
        } catch (MuxService.MuxException e) {
            log.error("MUX error: {}", e.getMessage(), e);
            throw new TransactionException("MUX not available", e);
        } catch (Exception e) {
            log.error("Transaction error: {}", e.getMessage(), e);
            throw new TransactionException("Transaction processing error", e);
        }
    }

    /**
     * Send ISO request via MUX, with automatic connection waiting if needed.
     */
    private ISOMsg sendRequest(ISOMsg isoRequest) throws MuxService.MuxException {
        log.info("Sending ISO 8583 message via MUX");
        log.debug("MUX connected: {}", muxService.isConnected());

        // Wait for MUX to be ready (max 5 seconds)
        if (!muxService.isConnected()) {
            log.info("MUX not connected, waiting for connection...");
            muxService.waitForReady(5000);
        }

        log.info("Sending request message...");
        ISOMsg isoResponse = muxService.request(isoRequest);

        if (isoResponse == null) {
            throw new TransactionException("No response from server");
        }

        return isoResponse;
    }

    /**
     * Verify MAC on response message.
     * Throws exception if MAC verification fails.
     */
    private void verifyResponseMac(ISOMsg isoResponse) throws Exception {
        boolean macValid = messageBuilder.verifyResponseMac(isoResponse);
        if (!macValid) {
            log.error("Response MAC verification failed - potential security issue!");
            throw new TransactionException("Response MAC verification failed");
        }
        log.debug("Response MAC verification successful");
    }

    /**
     * Parse ISO response and build TransactionResponse DTO.
     */
    private TransactionResponse parseResponse(ISOMsg isoResponse, TransactionRequest request) throws Exception {
        String responseCode = isoResponse.getString(39);
        String responseMessage = messageValidator.getResponseMessage(responseCode);
        BigDecimal balance = messageBuilder.parseBalanceFromResponse(isoResponse);

        log.info("Transaction completed:");
        log.info("  Response Code: {}", responseCode);
        log.info("  Response Message: {}", responseMessage);
        if (balance != null) {
            log.info("  Balance from server: {}", balance);
        }

        return TransactionResponse.builder()
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .balance(balance)
                .amount(request.getAmount())
                .timestamp(LocalDateTime.now())
                .terminalId(request.getTerminalId())
                .build();
    }

    /**
     * Mask PAN for logging (show first 6 and last 4 digits).
     */
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return "****";
        }
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
