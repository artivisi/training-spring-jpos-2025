package com.example.atm.web.controller;

import com.example.atm.domain.model.CryptoKey;
import com.example.atm.dto.KeyChangeRequest;
import com.example.atm.service.KeyChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/keys")
@RequiredArgsConstructor
@Slf4j
public class KeyManagementController {

    private final KeyChangeService keyChangeService;

    @GetMapping
    public String keyManagement(Model model) {
        model.addAttribute("keyChangeRequest", new KeyChangeRequest());
        return "key-management";
    }

    @PostMapping("/change")
    @ResponseBody
    public Map<String, Object> changeKey(@Valid @RequestBody KeyChangeRequest request,
                                        BindingResult bindingResult) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (bindingResult.hasErrors()) {
                response.put("success", false);
                response.put("message", "Invalid request");
                return response;
            }

            log.info("Initiating key change for key type: {}", request.getKeyType());

            // Convert DTO enum to entity enum
            CryptoKey.KeyType keyType = CryptoKey.KeyType.valueOf(request.getKeyType().name());

            // Initiate ISO-8583 key change request to server
            CryptoKey newKey = keyChangeService.initiateKeyChange(keyType);

            response.put("success", true);
            response.put("keyType", newKey.getKeyType());
            response.put("keyId", newKey.getId());
            response.put("checkValue", newKey.getCheckValue());
            response.put("message", "Key changed successfully via ISO-8583");

            log.info("Key change completed: keyType={}, keyId={}, KCV={}",
                    newKey.getKeyType(), newKey.getId(), newKey.getCheckValue());

        } catch (Exception e) {
            log.error("Key change failed", e);
            response.put("success", false);
            response.put("message", "Key change failed: " + e.getMessage());
        }

        return response;
    }
}
