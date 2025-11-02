package com.artivisi.atm.controller;

import com.artivisi.atm.entity.CryptoKey;
import com.artivisi.atm.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for key rotation operations.
 * Provides endpoints for terminal administrators to initiate key rotations.
 */
@RestController
@RequestMapping("/api/key-rotation")
@RequiredArgsConstructor
@Slf4j
public class KeyRotationController {

    private final KeyRotationService keyRotationService;

    /**
     * Initiate key rotation for a terminal.
     * This is a terminal-initiated (SCHEDULED) rotation.
     *
     * Example request:
     * POST /api/key-rotation/terminals/TRM-ISS001-ATM-001/rotate
     * {
     *   "keyType": "TPK",
     *   "gracePeriodHours": 24,
     *   "description": "Monthly scheduled rotation"
     * }
     *
     * @param terminalId Terminal identifier
     * @param request Rotation request parameters
     * @return Rotation ID and status
     */
    @PostMapping("/terminals/{terminalId}/rotate")
    public ResponseEntity<Map<String, Object>> rotateKey(
            @PathVariable String terminalId,
            @RequestBody RotationRequest request) {

        log.info("Received key rotation request for terminal: {}, keyType: {}",
                terminalId, request.getKeyType());

        try {
            // Parse key type
            CryptoKey.KeyType keyType = CryptoKey.KeyType.valueOf(request.getKeyType().toUpperCase());

            // Set default grace period if not provided
            Integer gracePeriod = request.getGracePeriodHours() != null
                    ? request.getGracePeriodHours()
                    : 24; // Default 24 hours

            // Initiate rotation
            String rotationId = keyRotationService.initiateKeyRotation(
                    terminalId,
                    keyType,
                    gracePeriod,
                    request.getDescription()
            );

            Map<String, Object> response = Map.of(
                    "status", "success",
                    "rotationId", rotationId,
                    "message", "Key rotation completed successfully"
            );

            log.info("Key rotation completed: terminal={}, rotationId={}", terminalId, rotationId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid key type: {}", request.getKeyType(), e);
            Map<String, Object> error = Map.of(
                    "status", "error",
                    "message", "Invalid key type. Must be TPK or TSK."
            );
            return ResponseEntity.badRequest().body(error);

        } catch (RuntimeException e) {
            log.error("Key rotation failed: {}", e.getMessage(), e);
            Map<String, Object> error = Map.of(
                    "status", "error",
                    "message", "Key rotation failed: " + e.getMessage()
            );
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Request DTO for key rotation.
     */
    public static class RotationRequest {
        private String keyType;
        private Integer gracePeriodHours;
        private String description;

        // Getters and setters
        public String getKeyType() {
            return keyType;
        }

        public void setKeyType(String keyType) {
            this.keyType = keyType;
        }

        public Integer getGracePeriodHours() {
            return gracePeriodHours;
        }

        public void setGracePeriodHours(Integer gracePeriodHours) {
            this.gracePeriodHours = gracePeriodHours;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
