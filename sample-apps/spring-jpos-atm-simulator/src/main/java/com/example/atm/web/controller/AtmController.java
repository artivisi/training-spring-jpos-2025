package com.example.atm.web.controller;

import com.example.atm.dto.TransactionRequest;
import com.example.atm.dto.TransactionResponse;
import com.example.atm.exception.TransactionException;
import com.example.atm.jpos.service.ISO8583MessageBuilder;
import com.example.atm.jpos.service.MessageValidator;
import com.example.atm.jpos.service.MuxService;
import com.example.atm.jpos.service.SignOnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/atm")
@RequiredArgsConstructor
@Slf4j
public class AtmController {

    private final ISO8583MessageBuilder messageBuilder;
    private final MessageValidator messageValidator;
    private final SignOnService signOnService;
    private final MuxService muxService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("transactionRequest", new TransactionRequest());
        return "index";
    }

    @PostMapping("/transaction")
    @ResponseBody
    public TransactionResponse executeTransaction(@Valid @RequestBody TransactionRequest request,
                                                  BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new TransactionException("Invalid request");
        }

        try {
            ISOMsg isoRequest = messageBuilder.buildTransactionRequest(request, request.getAccountNumber());

            log.info("Sending ISO 8583 message via MUX");
            log.debug("MUX connected: {}", muxService.isConnected());

            // Wait for MUX to be ready (max 5 seconds)
            if (!muxService.isConnected()) {
                muxService.waitForReady(5000);
            }

            log.info("Sending request message...");
            ISOMsg isoResponse = muxService.request(isoRequest);

            if (isoResponse == null) {
                throw new TransactionException("No response from server");
            }

            boolean macValid = messageBuilder.verifyResponseMac(isoResponse);
            if (!macValid) {
                log.error("Response MAC verification failed - potential security issue!");
                throw new TransactionException("Response MAC verification failed");
            }

            String responseCode = isoResponse.getString(39);
            String responseMessage = messageValidator.getResponseMessage(responseCode);

            BigDecimal balance = messageBuilder.parseBalanceFromResponse(isoResponse);

            log.info("Transaction completed:");
            log.info("  Response Code: {}", responseCode);
            log.info("  Response Message: {}", responseMessage);
            log.info("  Balance from server: {}", balance);

            return TransactionResponse.builder()
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .balance(balance)
                .amount(request.getAmount())
                .timestamp(LocalDateTime.now())
                .terminalId(request.getTerminalId())
                .build();

        } catch (ISOException e) {
            log.error("ISO message error", e);
            throw new TransactionException("ISO message error", e);
        } catch (MuxService.MuxException e) {
            log.error("MUX error: {}", e.getMessage(), e);
            throw new TransactionException("MUX not available", e);
        } catch (Exception e) {
            log.error("Transaction error", e);
            throw new TransactionException("Transaction processing error", e);
        }
    }

    /**
     * Get current sign-on status.
     */
    @GetMapping("/status/signon")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSignOnStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("signedOn", signOnService.isSignedOn());
        status.put("timestamp", LocalDateTime.now());

        log.debug("Sign-on status requested: {}", status.get("signedOn"));
        return ResponseEntity.ok(status);
    }

    /**
     * Manually trigger sign-on.
     */
    @PostMapping("/signon")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> signOn() {
        log.info("Manual sign-on requested");

        boolean success = signOnService.signOn();

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("signedOn", signOnService.isSignedOn());
        response.put("timestamp", LocalDateTime.now());
        response.put("message", success ? "Sign-on successful" : "Sign-on failed");

        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger sign-off.
     */
    @PostMapping("/signoff")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> signOff() {
        log.info("Manual sign-off requested");

        boolean success = signOnService.signOff();

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("signedOn", signOnService.isSignedOn());
        response.put("timestamp", LocalDateTime.now());
        response.put("message", success ? "Sign-off successful" : "Sign-off failed");

        return ResponseEntity.ok(response);
    }
}
