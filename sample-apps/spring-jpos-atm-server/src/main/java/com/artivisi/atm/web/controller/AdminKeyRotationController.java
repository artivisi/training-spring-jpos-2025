package com.artivisi.atm.web.controller;

import com.artivisi.atm.entity.CryptoKey;
import com.artivisi.atm.jpos.service.ChannelRegistry;
import com.artivisi.atm.service.ServerInitiatedKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST API for administrative key rotation operations.
 * Allows administrators to trigger server-initiated key rotation for terminals.
 */
@RestController
@RequestMapping("/api/admin/keys")
@RequiredArgsConstructor
@Slf4j
public class AdminKeyRotationController {

    private final ServerInitiatedKeyRotationService keyRotationService;
    private final ChannelRegistry channelRegistry;

    /**
     * Initiate key rotation for a specific terminal.
     *
     * @param terminalId Terminal identifier (e.g., "TRM-ISS001-ATM-001")
     * @param keyType Key type to rotate (TPK or TSK)
     * @return Response indicating success or failure
     */
    @PostMapping("/rotate/{terminalId}")
    public ResponseEntity<Map<String, Object>> initiateKeyRotation(
            @PathVariable String terminalId,
            @RequestParam(required = false, defaultValue = "TSK") String keyType) {

        log.info("Admin key rotation request: terminalId={}, keyType={}", terminalId, keyType);

        Map<String, Object> response = new HashMap<>();

        // Validate terminal is connected
        if (!keyRotationService.isTerminalConnected(terminalId)) {
            log.error("Terminal not connected: {}", terminalId);
            response.put("success", false);
            response.put("error", "Terminal not connected");
            response.put("terminalId", terminalId);
            return ResponseEntity.badRequest().body(response);
        }

        // Parse key type
        CryptoKey.KeyType keyTypeEnum;
        try {
            keyTypeEnum = CryptoKey.KeyType.valueOf(keyType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid key type: {}", keyType);
            response.put("success", false);
            response.put("error", "Invalid key type. Must be TPK or TSK");
            return ResponseEntity.badRequest().body(response);
        }

        // Initiate key rotation
        boolean success = keyRotationService.initiateKeyRotation(terminalId, keyTypeEnum);

        if (success) {
            response.put("success", true);
            response.put("message", "Key rotation notification sent to terminal");
            response.put("terminalId", terminalId);
            response.put("keyType", keyTypeEnum.name());
            response.put("nextStep", "Terminal will initiate standard key change flow (operation 01/02)");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", "Failed to send key rotation notification");
            response.put("terminalId", terminalId);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get list of currently connected terminals.
     *
     * @return List of terminal IDs with active connections
     */
    @GetMapping("/connected-terminals")
    public ResponseEntity<Map<String, Object>> getConnectedTerminals() {
        Set<String> connectedTerminals = channelRegistry.getConnectedTerminals();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", connectedTerminals.size());
        response.put("terminals", connectedTerminals);

        return ResponseEntity.ok(response);
    }

    /**
     * Check connection status for a specific terminal.
     *
     * @param terminalId Terminal identifier
     * @return Connection status
     */
    @GetMapping("/status/{terminalId}")
    public ResponseEntity<Map<String, Object>> getTerminalStatus(@PathVariable String terminalId) {
        boolean connected = channelRegistry.isConnected(terminalId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("terminalId", terminalId);
        response.put("connected", connected);

        return ResponseEntity.ok(response);
    }
}
