package com.example.atm.web.controller;

import com.example.atm.domain.model.CryptoKey;
import com.example.atm.dto.KeyChangeRequest;
import com.example.atm.service.CryptoKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/keys")
@RequiredArgsConstructor
@Slf4j
public class KeyManagementController {

    private final CryptoKeyService cryptoKeyService;

    @GetMapping
    public String keyManagement(Model model) {
        model.addAttribute("keyChangeRequest", new KeyChangeRequest());
        return "key-management";
    }

    @PostMapping("/change")
    @ResponseBody
    public Map<String, Object> changeKey(@Valid @RequestBody KeyChangeRequest request,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new RuntimeException("Invalid request");
        }

        log.info("Initiating key change for key type: {}", request.getKeyType());

        String newKeyValue = generateRandomKey();
        String checkValue = calculateCheckValue(newKeyValue);

        CryptoKey.KeyType keyType = CryptoKey.KeyType.valueOf(request.getKeyType().name());
        CryptoKey newKey = cryptoKeyService.rotateKey(keyType, newKeyValue, checkValue);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("keyType", newKey.getKeyType());
        response.put("keyId", newKey.getId());
        response.put("checkValue", newKey.getCheckValue());
        response.put("message", "Key changed successfully");

        return response;
    }

    private String generateRandomKey() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    private String calculateCheckValue(String keyValue) {
        return keyValue.substring(0, 6).toUpperCase();
    }
}
