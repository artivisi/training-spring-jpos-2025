package com.example.atm.web.controller;

import com.example.atm.domain.model.Account;
import com.example.atm.domain.model.Transaction;
import com.example.atm.dto.TransactionRequest;
import com.example.atm.dto.TransactionResponse;
import com.example.atm.exception.InsufficientBalanceException;
import com.example.atm.exception.InvalidPinException;
import com.example.atm.exception.TransactionException;
import com.example.atm.jpos.service.ISO8583MessageBuilder;
import com.example.atm.jpos.service.MessageValidator;
import com.example.atm.jpos.service.PINBlockService;
import com.example.atm.service.AccountService;
import com.example.atm.service.TransactionService;
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

import java.time.LocalDateTime;

@Controller
@RequestMapping("/atm")
@RequiredArgsConstructor
@Slf4j
public class AtmController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final PINBlockService pinBlockService;
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
            Account account = accountService.findByPan(request.getPan());

            if (!accountService.verifyPin(account, request.getPin())) {
                throw new InvalidPinException("Invalid PIN");
            }

            if (request.getAmount() != null &&
                account.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }

            String pinBlock = pinBlockService.calculatePINBlock(request.getPin(), request.getPan());
            ISOMsg isoRequest = messageBuilder.buildTransactionRequest(request, pinBlock);

            log.info("Sending ISO 8583 message via MUX: {}", muxName);
            MUX mux = (MUX) NameRegistrar.get(muxName);
            ISOMsg isoResponse = mux.request(isoRequest, 30000);

            if (isoResponse == null) {
                throw new TransactionException("No response from server");
            }

            String responseCode = isoResponse.getString(39);
            String responseMessage = messageValidator.getResponseMessage(responseCode);

            Transaction.TransactionStatus status = "00".equals(responseCode) ?
                Transaction.TransactionStatus.SUCCESS : Transaction.TransactionStatus.FAILED;

            transactionService.createTransaction(
                account.getId(),
                request,
                new String(isoRequest.pack()),
                new String(isoResponse.pack()),
                responseCode,
                status
            );

            if ("00".equals(responseCode) && request.getAmount() != null &&
                request.getType() == TransactionRequest.TransactionType.WITHDRAWAL) {
                accountService.debit(account.getId(), request.getAmount());
            }

            return TransactionResponse.builder()
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .balance(account.getBalance())
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
