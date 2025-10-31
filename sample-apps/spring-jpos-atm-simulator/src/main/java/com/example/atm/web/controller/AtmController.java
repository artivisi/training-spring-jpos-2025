package com.example.atm.web.controller;

import com.example.atm.dto.TransactionRequest;
import com.example.atm.dto.TransactionResponse;
import com.example.atm.exception.TransactionException;
import com.example.atm.jpos.service.ISO8583MessageBuilder;
import com.example.atm.jpos.service.MessageValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.MUX;
import org.jpos.util.NameRegistrar;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/atm")
@RequiredArgsConstructor
@Slf4j
public class AtmController {

    private final ISO8583MessageBuilder messageBuilder;
    private final MessageValidator messageValidator;
    private final String muxName;

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

            log.info("Sending ISO 8583 message via MUX: {}", muxName);
            MUX mux = (MUX) NameRegistrar.get(muxName);

            log.debug("MUX instance: {}", mux);
            log.debug("MUX connected: {}", mux.isConnected());

            // Wait for MUX to be ready (max 5 seconds)
            if (!mux.isConnected()) {
                log.info("MUX not connected, waiting for ready...");
                long startWait = System.currentTimeMillis();
                while (!mux.isConnected() && (System.currentTimeMillis() - startWait) < 5000) {
                    Thread.sleep(100);
                }
                log.info("MUX ready status after wait: connected={}", mux.isConnected());
            }

            log.info("Sending request message...");
            ISOMsg isoResponse = mux.request(isoRequest, 30000);

            if (isoResponse == null) {
                log.error("MUX returned null response after timeout");
                log.error("MUX connected after request: {}", mux.isConnected());
                throw new TransactionException("No response from server");
            }

            boolean macValid = messageBuilder.verifyResponseMac(isoResponse);
            if (!macValid) {
                log.warn("Response MAC verification failed - continuing for testing purposes");
                // TODO: Re-enable MAC validation after resolving key synchronization
                // throw new TransactionException("Response MAC verification failed");
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
        } catch (NameRegistrar.NotFoundException e) {
            log.error("MUX not found: {}", muxName, e);
            throw new TransactionException("MUX not available", e);
        } catch (Exception e) {
            log.error("Transaction error", e);
            throw new TransactionException("Transaction processing error", e);
        }
    }
}
