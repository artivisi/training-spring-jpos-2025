package com.example.atm.jpos.util;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;

/**
 * Utility class for terminal identification operations in ISO-8583 messages.
 * Handles extraction, formatting, and splitting of terminal IDs.
 *
 * Terminal ID Format:
 * - Full ID: "INSTITUTION-TERMINAL" (e.g., "TRM-ISS001-ATM-001")
 * - Field 42 (Card Acceptor ID): Institution code (e.g., "TRM-ISS001")
 * - Field 41 (Terminal ID): Terminal code (e.g., "ATM-001")
 *
 * Field Lengths (per ISO-8583 spec):
 * - Field 41: 15 characters (ANSII, left-aligned, space-padded)
 * - Field 42: 15 characters (ANSII, left-aligned, space-padded)
 */
@Slf4j
public class TerminalIdUtil {

    public static final int FIELD_41_LENGTH = 15;
    public static final int FIELD_42_LENGTH = 15;

    /**
     * Extract full terminal ID from ISO message by combining field 42 and field 41.
     *
     * @param msg ISO message
     * @return Full terminal ID in format "INSTITUTION-TERMINAL", or null if field 41 missing
     */
    public static String extractTerminalId(ISOMsg msg) {
        try {
            String cardAcceptorId = msg.getString(42);  // Field 42: Institution
            String terminalId = msg.getString(41);       // Field 41: Terminal

            if (terminalId == null || terminalId.trim().isEmpty()) {
                return null;
            }

            // Combine both fields if card acceptor ID is present
            if (cardAcceptorId != null && !cardAcceptorId.trim().isEmpty()) {
                return cardAcceptorId.trim() + "-" + terminalId.trim();
            }

            return terminalId.trim();

        } catch (Exception e) {
            log.debug("Could not extract terminal ID from message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Split full terminal ID into institution and terminal components.
     *
     * @param fullTerminalId Full terminal ID (e.g., "TRM-ISS001-ATM-001")
     * @return Array [institution, terminal], or [null, fullTerminalId] if no institution part
     */
    public static String[] splitTerminalId(String fullTerminalId) {
        if (fullTerminalId == null || fullTerminalId.trim().isEmpty()) {
            return new String[]{null, null};
        }

        String trimmed = fullTerminalId.trim();

        // Split into max 3 parts to handle "TRM-ISS001-ATM-001" format
        String[] parts = trimmed.split("-", 3);

        if (parts.length == 3) {
            // Format: "TRM-ISS001-ATM-001" -> institution="TRM-ISS001", terminal="ATM-001"
            String institution = parts[0] + "-" + parts[1];
            String terminal = parts[2];
            return new String[]{institution, terminal};
        } else if (parts.length == 2) {
            // Format: "INSTITUTION-TERMINAL"
            return new String[]{parts[0], parts[1]};
        } else {
            // Single part - no institution
            return new String[]{null, trimmed};
        }
    }

    /**
     * Format terminal ID for ISO-8583 field 41 (15 characters, left-aligned, space-padded).
     *
     * @param terminalId Terminal ID string
     * @return Formatted string (15 chars)
     */
    public static String formatField41(String terminalId) {
        if (terminalId == null) {
            return String.format("%-15s", "");
        }
        return String.format("%-15s", terminalId);
    }

    /**
     * Format institution ID for ISO-8583 field 42 (15 characters, left-aligned, space-padded).
     *
     * @param institutionId Institution ID string
     * @return Formatted string (15 chars)
     */
    public static String formatField42(String institutionId) {
        if (institutionId == null) {
            return String.format("%-15s", "");
        }
        return String.format("%-15s", institutionId);
    }

    /**
     * Set terminal identification fields in ISO message.
     * Splits full terminal ID and sets both field 41 and field 42.
     *
     * @param msg ISO message to update
     * @param fullTerminalId Full terminal ID (e.g., "TRM-ISS001-ATM-001")
     * @throws Exception if setting fields fails
     */
    public static void setTerminalIdFields(ISOMsg msg, String fullTerminalId) throws Exception {
        String[] parts = splitTerminalId(fullTerminalId);
        String institution = parts[0];
        String terminal = parts[1];

        if (terminal != null) {
            msg.set(41, formatField41(terminal));
        }

        if (institution != null) {
            msg.set(42, formatField42(institution));
        }
    }

    /**
     * Get terminal component from full terminal ID.
     * Example: "TRM-ISS001-ATM-001" -> "ATM-001"
     *
     * @param fullTerminalId Full terminal ID
     * @return Terminal component only
     */
    public static String getTerminalComponent(String fullTerminalId) {
        String[] parts = splitTerminalId(fullTerminalId);
        return parts[1];
    }

    /**
     * Get institution component from full terminal ID.
     * Example: "TRM-ISS001-ATM-001" -> "TRM-ISS001"
     *
     * @param fullTerminalId Full terminal ID
     * @return Institution component, or null if not present
     */
    public static String getInstitutionComponent(String fullTerminalId) {
        String[] parts = splitTerminalId(fullTerminalId);
        return parts[0];
    }
}
