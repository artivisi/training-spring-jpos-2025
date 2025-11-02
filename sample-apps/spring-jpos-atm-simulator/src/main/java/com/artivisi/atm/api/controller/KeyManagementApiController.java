package com.artivisi.atm.api.controller;

import com.artivisi.atm.domain.model.CryptoKey;
import com.artivisi.atm.dto.KeyChangeRequest;
import com.artivisi.atm.service.KeyChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for cryptographic key management.
 * Provides JSON endpoints for key rotation operations.
 * Used by testing tools and external integrations.
 */
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
@Slf4j
public class KeyManagementApiController {

    private final KeyChangeService keyChangeService;

    /**
     * Initiate key change operation.
     *
     * @param request Key change request with key type (TPK or TSK)
     * @return Key change result with new key details
     */
    @PostMapping("/change")
    public ResponseEntity<Map<String, Object>> changeKey(@Valid @RequestBody KeyChangeRequest request) {
        log.info("API: Key change request for key type: {}", request.getKeyType());

        Map<String, Object> response = new HashMap<>();

        try {
            // Convert DTO enum to entity enum
            CryptoKey.KeyType keyType = CryptoKey.KeyType.valueOf(request.getKeyType().name());

            // Initiate ISO-8583 key change request to server
            CryptoKey newKey = keyChangeService.initiateKeyChange(keyType);

            response.put("success", true);
            response.put("keyType", newKey.getKeyType().name());
            response.put("keyId", newKey.getId().toString());
            response.put("checkValue", newKey.getCheckValue());
            response.put("message", "Key changed successfully via ISO-8583");

            log.info("API: Key change completed: keyType={}, keyId={}, KCV={}",
                    newKey.getKeyType(), newKey.getId(), newKey.getCheckValue());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("API: Key change failed: {}", e.getMessage(), e);

            response.put("success", false);
            response.put("message", "Key change failed: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }
}
