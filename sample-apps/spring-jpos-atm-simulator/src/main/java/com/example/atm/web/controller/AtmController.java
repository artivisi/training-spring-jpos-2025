package com.example.atm.web.controller;

import com.example.atm.dto.TransactionRequest;
import com.example.atm.dto.TransactionResponse;
import com.example.atm.exception.TransactionException;
import com.example.atm.jpos.service.SignOnService;
import com.example.atm.service.AtmTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Web controller for ATM simulator UI.
 * Handles Thymeleaf page rendering and form submissions.
 * Business logic is delegated to AtmTransactionService.
 */
@Controller
@RequestMapping("/atm")
@RequiredArgsConstructor
@Slf4j
public class AtmController {

    private final AtmTransactionService atmTransactionService;
    private final SignOnService signOnService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("transactionRequest", new TransactionRequest());
        return "index";
    }

    /**
     * Execute ATM transaction (balance inquiry or withdrawal).
     * Delegates to AtmTransactionService for business logic.
     */
    @PostMapping("/transaction")
    @ResponseBody
    public TransactionResponse executeTransaction(@Valid @RequestBody TransactionRequest request,
                                                  BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new TransactionException("Invalid request");
        }

        log.info("Web: Transaction request received: type={}", request.getType());

        // Delegate to service layer
        return atmTransactionService.executeTransaction(request);
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
