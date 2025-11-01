package com.example.atm.util;

import org.jpos.iso.ISOMsg;

/**
 * Utility class for terminal identification operations in ISO-8583 messages.
 * Handles formatting and setting of terminal identification fields.
 *
 * Terminal ID Format:
 * - Field 41 (Terminal ID): Terminal code (e.g., "ATM-001")
 * - Field 42 (Card Acceptor ID): Institution code (e.g., "TRM-ISS001")
 *
 * Field Lengths (per ISO-8583 spec):
 * - Field 41: 15 characters (ANSII, left-aligned, space-padded)
 * - Field 42: 15 characters (ANSII, left-aligned, space-padded)
 */
public class TerminalIdUtil {

    public static final int FIELD_41_LENGTH = 15;
    public static final int FIELD_42_LENGTH = 15;

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
     *
     * @param msg ISO message to update
     * @param terminalId Terminal ID (e.g., "ATM-001")
     * @param institutionId Institution ID (e.g., "TRM-ISS001")
     * @throws Exception if setting fields fails
     */
    public static void setTerminalIdFields(ISOMsg msg, String terminalId, String institutionId) throws Exception {
        msg.set(41, formatField41(terminalId));
        msg.set(42, formatField42(institutionId));
    }
}
