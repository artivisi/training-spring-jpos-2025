# HSM Integration Guide

## Overview

This application integrates with HSM Simulator to verify PIN blocks received in ISO-8583 messages. The system uses a **two-key cryptography approach**:

1. **TPK (Terminal PIN Key)**: Encrypts PIN from ATM to server
2. **LMK (Local Master Key)**: HSM's internal key for storing PINs

## Architecture

The design keeps jPOS layer minimal by delegating PIN verification to BankService:

```
ISO-8583 Message (field 52 = TPK-encrypted PIN)
    ↓
PinVerificationParticipant (jPOS layer - MINIMAL)
    ├─ Extracts field 52 (PIN block)
    ├─ Extracts field 2 (PAN)
    ├─ Extracts field 102 (account number)
    └─ Calls BankService.verifyPin()
    ↓
BankService.verifyPin() (Business logic - REUSABLE)
    ├─ Fetches account from database
    ├─ Retrieves stored PIN (LMK-encrypted)
    └─ Calls HsmService
    ↓
HsmService → HSM Simulator
    ├─ Decrypts incoming PIN using TPK
    ├─ Decrypts stored PIN using LMK
    ├─ Compares both PINs
    └─ Returns result
    ↓
BankService → throws exception if invalid
    ↓
PinVerificationParticipant
    ├─ If exception: set response code 55, ABORT
    └─ If success: continue to BalanceInquiry/Withdrawal
```

### Benefits of This Design

1. **Separation of Concerns**: jPOS participants stay minimal, business logic in service layer
2. **Reusability**: `BankService.verifyPin()` can be called from:
   - jPOS participants (ISO-8583 flow)
   - REST controllers (if needed)
   - Other business logic
3. **No Code Duplication**: Single PIN verification method for all entry points
4. **Testability**: BankService can be unit tested independently

### Key Concepts

**PIN Storage:**
- PINs are **NEVER stored in clear text**
- During PIN setup, clear PIN is encrypted under HSM's LMK
- LMK-encrypted PIN block is stored in `accounts.encrypted_pin_block` column
- LMK never leaves the HSM

**PIN Verification Flow:**
1. ATM encrypts customer's PIN under TPK → sends in field 52
2. Server fetches stored PIN (LMK-encrypted) from database
3. Server sends **both** PIN blocks to HSM:
   - Incoming PIN block (TPK-encrypted)
   - Stored PIN block (LMK-encrypted)
4. HSM decrypts both using respective keys and compares
5. HSM returns comparison result

## Configuration

### Application Configuration (application.yml)

```yaml
hsm:
  url: http://localhost:8080
  pin:
    verification:
      endpoint: /api/hsm/pin/verify-with-translation
    terminal-id: ATM00001
    format: ISO_0
  connection:
    timeout: 5000
    read-timeout: 10000
```

### Parameters:
- `hsm.url`: Base URL of HSM simulator
- `hsm.pin.verification.endpoint`: Endpoint path for PIN block verification
- `hsm.pin.terminal-id`: Terminal identifier (HSM uses this to lookup the appropriate TPK for decryption)
- `hsm.pin.format`: PIN block format enum value (ISO_0, ISO_1, ISO_3, or ISO_4)
- `hsm.connection.timeout`: Connection timeout in milliseconds
- `hsm.connection.read-timeout`: Read timeout in milliseconds

## HSM Simulator Integration

### Method A: Verify PIN with Translation

This application uses the HSM Simulator's **Method A** endpoint for PIN verification with translation.

**Endpoint:** `POST /api/hsm/pin/verify-with-translation`

**Purpose:** Verifies a PIN by comparing encrypted PIN blocks from two sources:
- PIN block from terminal (encrypted under TPK)
- PIN block from database (encrypted under LMK)

**Request Body:**
```json
{
  "pinBlockUnderTPK": "ABC123DEF456...",
  "pinBlockUnderLMK": "789DEF012345...",
  "terminalId": "ATM00001",
  "pan": "1234567890123456",
  "pinFormat": "ISO-0"
}
```

**Request Parameters:**
- `pinBlockUnderTPK` (required): Hex-encoded PIN block from ISO-8583 field 52, encrypted under terminal's TPK
- `pinBlockUnderLMK` (required): Hex-encoded PIN block from database, encrypted under HSM's LMK
- `terminalId` (required): Terminal identifier - HSM uses this to lookup the appropriate TPK
- `pan` (required): Primary Account Number from ISO-8583 field 2, required for PIN block formats like ISO-0
- `pinFormat` (optional): PIN block format (ISO-0, ISO-1, ISO-3, ISO-4), defaults to ISO-0

**Note:** Internally, the application uses a `PinFormat` enum (ISO_0, ISO_1, ISO_3, ISO_4) which serializes to JSON as the hyphenated string format (ISO-0, ISO-1, ISO-3, ISO-4).

**Response Body:**
```json
{
  "valid": true,
  "message": "PIN verification successful",
  "terminalId": "ATM00001",
  "pan": "1234567890123456",
  "pinFormat": "ISO-0",
  "lmkKeyId": "lmk-master-001",
  "tpkKeyId": "tpk-atm00001"
}
```

**Response Parameters:**
- `valid` (boolean): true if PINs match, false otherwise
- `message` (string): Descriptive message about verification result
- `terminalId` (string): Echo of terminal identifier
- `pan` (string): Echo of account number
- `pinFormat` (string): Format used for verification
- `lmkKeyId` (string): LMK key identifier used by HSM
- `tpkKeyId` (string): TPK key identifier used by HSM

**HTTP Status Codes:**
- `200 OK`: Request processed successfully (check `valid` field for result)
- `400 Bad Request`: Invalid request parameters
- `500 Internal Server Error`: HSM processing error

### Implementation Notes

The HSM endpoint should:

1. **Decrypt PIN block** using the specified TPK key
2. **Extract clear PIN** according to the PIN block format
3. **Retrieve stored PIN** or PIN offset for the account number
4. **Compare** the extracted PIN with stored PIN
5. **Return verification result**

### Processing Flow (Method A)

1. Terminal sends encrypted PIN block (TPK) + PAN to core banking application
2. Application queries stored PIN block (LMK) from database
3. Both encrypted blocks are sent to HSM along with terminal ID and PAN
4. HSM looks up TPK for the terminal using `terminalId`
5. HSM decrypts `pinBlockUnderTPK` using the terminal's TPK
6. HSM decrypts `pinBlockUnderLMK` using the master LMK
7. HSM extracts clear PINs from both decrypted blocks using the specified format
8. Both clear PINs are compared for equality
9. HSM returns verification result with key IDs used

**Key Benefits of Method A:**
- Detailed audit trail with key IDs in response
- Clear separation between TPK (terminal security) and LMK (database security)
- Educational visibility into HSM operations
- No clear PINs ever leave the HSM boundary

## Transaction Flow

### Participant Chain

```
1. PinVerificationParticipant
   └─ Extracts field 52 (PIN block), field 2 (PAN), field 102 (account)
   └─ Calls BankService.verifyPin() (delegates to business layer)
   └─ If exception: Set response code 55, ABORT transaction
   └─ If success: Continue to next participant

2. BalanceInquiryParticipant
   └─ Processes balance inquiry (processing code 310000)
   └─ Calls BankService.balanceInquiry()

3. WithdrawalParticipant
   └─ Processes withdrawal (processing code 010000)
   └─ Calls BankService.withdraw()

4. ResponseBuilderParticipant
   └─ Builds ISO-8583 response message

5. SendResponseParticipant
   └─ Sends response back to client
```

### Service Layer Method

The `BankService.verifyPin()` method can be called from anywhere:

```java
public class BankService {
    public void verifyPin(String accountNumber, String pinBlock, String pan) {
        // 1. Fetch account from database
        // 2. Get stored PIN block (LMK-encrypted)
        // 3. Call HsmService to verify
        // 4. Throw exception if invalid
    }
}
```

This method is used by:
- **PinVerificationParticipant**: For ISO-8583 transactions
- **REST controllers**: If you add REST endpoints for PIN operations
- **Other business logic**: Any service that needs PIN verification

### ISO-8583 Response Codes

| Code | Description | When Used |
|------|-------------|-----------|
| 00 | Approved | Transaction successful |
| 14 | Invalid account | Account not found |
| 30 | Format error | Missing required fields |
| 51 | Insufficient funds | Not enough balance for withdrawal |
| 55 | Invalid PIN | PIN verification failed |
| 62 | Security violation | Account not active or security issue |
| 96 | System malfunction | Internal error or HSM communication failure |

## Testing

### Test ISO-8583 Message with PIN Block

Example message with PIN block in field 52:

```
MTI: 0200 (Financial Transaction Request)
Field 2: 1234567890123456 (PAN)
Field 3: 010000 (Withdrawal)
Field 4: 000000050000 (Amount: $500.00)
Field 7: 0129153045 (Transmission date/time)
Field 11: 123456 (STAN)
Field 41: ATM00001 (Terminal ID)
Field 52: ABC123DEF456... (Encrypted PIN block)
Field 102: 123456789012 (Account number)
```

### Expected Behavior

**Valid PIN:**
- PinVerificationParticipant: PIN verified, continues
- Transaction processed normally
- Response code: 00 (or specific to transaction result)

**Invalid PIN:**
- PinVerificationParticipant: PIN verification failed
- Transaction aborted
- Response code: 55 (Invalid PIN)

**Missing PIN Block:**
- PinVerificationParticipant: Skips verification (logs warning)
- Transaction continues without PIN check

**HSM Communication Error:**
- PinVerificationParticipant: Exception caught
- Transaction aborted
- Response code: 96 (System malfunction)

## Security Considerations

1. **Transport Security**: Use HTTPS for HSM communication in production
2. **Key Management**: TPK keys must be properly managed and rotated
3. **Audit Logging**: All PIN verification attempts should be logged
4. **Timeout Configuration**: Set appropriate timeouts to prevent hanging connections
5. **Error Handling**: Never expose internal HSM errors to clients

## Monitoring

Log messages to monitor:

```
INFO  - Verifying PIN for account: {accountNumber}
INFO  - PIN verification successful for account: {accountNumber}
WARN  - PIN verification failed for account: {accountNumber}
ERROR - Error communicating with HSM for account: {accountNumber}
```

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| Response code 96 | HSM not reachable | Check HSM URL and network connectivity |
| Response code 96 | HSM timeout | Increase timeout values in configuration |
| Response code 55 | Wrong PIN | Verify PIN encryption and TPK configuration |
| PIN verification skipped | Field 52 missing | Ensure ATM sends PIN block in field 52 |
| Exception in logs | HSM endpoint not found | Implement PIN verification endpoint in HSM |
