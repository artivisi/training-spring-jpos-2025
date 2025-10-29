# HSM Integration Guide

## Overview

This application integrates with HSM Simulator to verify PIN blocks received in ISO-8583 messages. The system supports **two verification methods**:

1. **Encrypted PIN Block Verification (Translation Method)**: Uses two-key cryptography (TPK + LMK)
2. **PVV (PIN Verification Value) Method**: Uses one-way hash (ISO 9564 compliant)

### Key Cryptographic Keys

- **TPK (Terminal PIN Key)**: Encrypts PIN from ATM to server
- **LMK (Local Master Key)**: HSM's internal key for storing encrypted PIN blocks
- **PVK (PIN Verification Key)**: HSM's key for generating/verifying PVV values

## Architecture

The design uses a **Strategy Pattern** to support multiple PIN verification methods, keeping the jPOS layer minimal:

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
    ├─ Checks account.pinVerificationType
    └─ Calls HsmService.verifyPin(pinBlock, pan, account)
    ↓
HsmService (Strategy Coordinator)
    ├─ Selects appropriate strategy based on account.pinVerificationType
    ├─ ENCRYPTED_PIN_BLOCK → EncryptedPinBlockVerificationStrategy
    └─ PVV → PvvVerificationStrategy
    ↓
┌─────────────────────────────────────┬─────────────────────────────────────┐
│ EncryptedPinBlockVerificationStrategy│ PvvVerificationStrategy              │
├─────────────────────────────────────┼─────────────────────────────────────┤
│ 1. Get account.encryptedPinBlock   │ 1. Get account.pvv                   │
│ 2. Call HsmClient.verifyPinBlock()  │ 2. Call HsmClient.verifyWithPvv()    │
│ 3. HSM decrypts both PIN blocks     │ 3. HSM decrypts PIN block            │
│ 4. HSM compares decrypted PINs      │ 4. HSM generates PVV from PIN        │
│ 5. Return comparison result         │ 5. HSM compares with stored PVV      │
└─────────────────────────────────────┴─────────────────────────────────────┘
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
5. **Extensibility**: Strategy pattern allows adding new verification methods without modifying existing code
6. **Flexibility**: Each account can use different verification method based on configuration

### Key Concepts

**PIN Verification Method Selection:**
- Each account has a `pin_verification_type` field (ENCRYPTED_PIN_BLOCK or PVV)
- System automatically selects the appropriate verification strategy at runtime
- Defaults to ENCRYPTED_PIN_BLOCK for backward compatibility

**Method 1: Encrypted PIN Block Verification (Translation)**

Storage:
- PINs are **NEVER stored in clear text**
- During PIN setup, clear PIN is encrypted under HSM's LMK
- LMK-encrypted PIN block is stored in `accounts.encrypted_pin_block` column
- LMK never leaves the HSM

Verification Flow:
1. ATM encrypts customer's PIN under TPK → sends in field 52
2. Server fetches stored PIN block (LMK-encrypted) from database
3. Server sends **both** PIN blocks to HSM:
   - Incoming PIN block (TPK-encrypted)
   - Stored PIN block (LMK-encrypted)
4. HSM decrypts both using respective keys and compares
5. HSM returns comparison result

**Method 2: PVV (PIN Verification Value)**

Storage:
- PINs are **NEVER stored in clear text**
- During PIN setup, HSM generates a 4-digit PVV using one-way hash (ISO 9564)
- Only the PVV is stored in `accounts.pvv` column (4 digits)
- Original PIN cannot be derived from PVV (one-way function)

Verification Flow:
1. ATM encrypts customer's PIN under TPK → sends in field 52
2. Server fetches stored PVV (4 digits) from database
3. Server sends PIN block and stored PVV to HSM:
   - Incoming PIN block (TPK-encrypted)
   - Stored PVV (4 digits)
4. HSM decrypts PIN block using TPK
5. HSM generates PVV from decrypted PIN using same algorithm
6. HSM compares generated PVV with stored PVV
7. HSM returns comparison result

**Why Use PVV?**
- More secure: Only 4-digit hash stored instead of encrypted full PIN
- ISO 9564 compliant
- Most common method in banking systems
- Even if database is compromised, PVV cannot be reversed to get PIN

## Configuration

### Application Configuration (application.yml)

```yaml
hsm:
  url: http://localhost:8080
  pin:
    encrypted-pin-block:
      endpoint: /api/hsm/pin/verify-with-translation
    pvv:
      endpoint: /api/hsm/pin/verify-with-pvv
    terminal-id: ATM00001
    format: ISO_0
  connection:
    timeout: 5000
    read-timeout: 10000
```

### Parameters:
- `hsm.url`: Base URL of HSM simulator
- `hsm.pin.encrypted-pin-block.endpoint`: Endpoint for encrypted PIN block verification (translation method)
- `hsm.pin.pvv.endpoint`: Endpoint for PVV verification
- `hsm.pin.terminal-id`: Terminal identifier (HSM uses this to lookup the appropriate TPK for decryption)
- `hsm.pin.format`: PIN block format enum value (ISO_0, ISO_1, ISO_3, or ISO_4)
- `hsm.connection.timeout`: Connection timeout in milliseconds
- `hsm.connection.read-timeout`: Read timeout in milliseconds

## HSM Simulator Integration

The application integrates with two HSM endpoints for different verification methods.

### Encrypted PIN Block Verification (Translation Method)

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

**Key Benefits:**
- Detailed audit trail with key IDs in response
- Clear separation between TPK (terminal security) and LMK (database security)
- Educational visibility into HSM operations
- No clear PINs ever leave the HSM boundary

### PVV (PIN Verification Value) Method

**Endpoint:** `POST /api/hsm/pin/verify-with-pvv`

**Purpose:** Verifies a PIN using PVV (PIN Verification Value) - the most common method in banking systems (ISO 9564 compliant).

**Request Body:**
```json
{
  "pinBlockUnderTPK": "ABC123DEF456...",
  "storedPVV": "1234",
  "terminalId": "ATM00001",
  "pan": "1234567890123456",
  "pinFormat": "ISO-0"
}
```

**Request Parameters:**
- `pinBlockUnderTPK` (required): Hex-encoded PIN block from ISO-8583 field 52, encrypted under terminal's TPK
- `storedPVV` (required): 4-digit PVV from database
- `terminalId` (required): Terminal identifier - HSM uses this to lookup the appropriate TPK
- `pan` (required): Primary Account Number from ISO-8583 field 2
- `pinFormat` (optional): PIN block format (ISO-0, ISO-1, ISO-3, ISO-4), defaults to ISO-0

**Response Body:**
```json
{
  "valid": true,
  "message": "PVV verification successful",
  "method": "PVV",
  "terminalId": "ATM00001",
  "pan": "1234567890123456",
  "pinFormat": "ISO-0",
  "tpkKeyId": "tpk-atm00001",
  "pvkKeyId": "pvk-master-001",
  "storedPVV": "1234"
}
```

**Response Parameters:**
- `valid` (boolean): true if PVV matches, false otherwise
- `message` (string): Descriptive message about verification result
- `method` (string): Verification method used ("PVV")
- `terminalId` (string): Echo of terminal identifier
- `pan` (string): Echo of account number
- `pinFormat` (string): Format used for verification
- `tpkKeyId` (string): TPK key identifier used by HSM
- `pvkKeyId` (string): PVK key identifier used for PVV generation
- `storedPVV` (string): Echo of stored PVV

**HTTP Status Codes:**
- `200 OK`: Request processed successfully (check `valid` field for result)
- `400 Bad Request`: Invalid request parameters
- `500 Internal Server Error`: HSM processing error

### Processing Flow (PVV Method)

1. Terminal sends encrypted PIN block (TPK) + PAN to core banking application
2. Application queries stored PVV (4 digits) from database
3. PIN block and stored PVV are sent to HSM along with terminal ID and PAN
4. HSM looks up TPK for the terminal using `terminalId`
5. HSM decrypts `pinBlockUnderTPK` using the terminal's TPK
6. HSM extracts clear PIN from decrypted block using the specified format
7. HSM generates PVV from clear PIN using PVK and one-way hash (ISO 9564)
8. Generated PVV is compared with `storedPVV`
9. HSM returns verification result with key IDs used

**Key Benefits:**
- Most secure: Only 4-digit hash stored, cannot reverse to get PIN
- ISO 9564 compliant
- Standard method used in banking industry
- Even if database is compromised, PVV cannot be used to derive PIN
- Smaller storage footprint (4 digits vs full encrypted block)

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

### Service Layer Implementation

The system uses a layered architecture with Strategy pattern for PIN verification:

**BankService.verifyPin()** - Entry point for PIN verification:
```java
public class BankService {
    public void verifyPin(String accountNumber, String pinBlock, String pan) {
        // 1. Fetch account from database
        Account account = accountRepository.findByAccountNumber(accountNumber);

        // 2. Delegate to HsmService with full account object
        boolean pinValid = hsmService.verifyPin(pinBlock, pan, account);

        // 3. Throw exception if invalid
        if (!pinValid) {
            throw new RuntimeException("Invalid PIN");
        }
    }
}
```

**HsmService.verifyPin()** - Strategy coordinator:
```java
public class HsmService {
    private Map<PinVerificationType, PinVerificationStrategy> strategyMap;

    public boolean verifyPin(String pinBlock, String pan, Account account) {
        // Select strategy based on account's verification type
        PinVerificationType type = account.getPinVerificationType();
        PinVerificationStrategy strategy = strategyMap.get(type);

        // Execute verification using selected strategy
        return strategy.verify(pinBlock, pan, account);
    }
}
```

**Strategy Implementations:**
```java
// For accounts using encrypted PIN block method
public class EncryptedPinBlockVerificationStrategy {
    public boolean verify(String pinBlock, String pan, Account account) {
        String storedPinBlock = account.getEncryptedPinBlock();
        return hsmClient.verifyPinBlock(request).isValid();
    }
}

// For accounts using PVV method
public class PvvVerificationStrategy {
    public boolean verify(String pinBlock, String pan, Account account) {
        String storedPvv = account.getPvv();
        return hsmClient.verifyWithPvv(request).isValid();
    }
}
```

**Usage contexts:**
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

### Database Setup for Testing

To test different verification methods, configure accounts with appropriate verification types:

**Encrypted PIN Block Method:**
```sql
INSERT INTO accounts (account_number, encrypted_pin_block, pin_verification_type)
VALUES ('1234567890', 'ABCD1234567890ABCDEF1234567890AB', 'ENCRYPTED_PIN_BLOCK');
```

**PVV Method:**
```sql
INSERT INTO accounts (account_number, pvv, pin_verification_type)
VALUES ('0987654321', '1234', 'PVV');
```

### Test ISO-8583 Message with PIN Block

Example message with PIN block in field 52:

```
MTI: 0200 (Financial Transaction Request)
Field 2: 4111111111111111 (PAN)
Field 3: 010000 (Withdrawal) or 310000 (Balance Inquiry)
Field 4: 000000050000 (Amount: $500.00)
Field 7: 0129153045 (Transmission date/time)
Field 11: 123456 (STAN)
Field 41: ATM00001 (Terminal ID)
Field 52: ABC123DEF456... (Encrypted PIN block under TPK)
Field 102: 1234567890 (Account number)
```

**Note:** The PIN block in field 52 is always encrypted under TPK, regardless of the account's verification method. The system automatically selects the appropriate verification strategy based on the account's `pin_verification_type`.

### Expected Behavior

**Valid PIN (Encrypted PIN Block Account):**
- System uses `EncryptedPinBlockVerificationStrategy`
- Sends both TPK-encrypted and LMK-encrypted PIN blocks to HSM
- HSM compares decrypted PINs
- Transaction processed normally
- Response code: 00

**Valid PIN (PVV Account):**
- System uses `PvvVerificationStrategy`
- Sends TPK-encrypted PIN block and stored PVV to HSM
- HSM generates PVV from decrypted PIN and compares
- Transaction processed normally
- Response code: 00

**Invalid PIN (Either Method):**
- Appropriate strategy verifies PIN
- PIN verification fails
- Transaction aborted
- Response code: 55 (Invalid PIN)

**Missing PIN Block:**
- BankService skips verification (logs debug message)
- Transaction continues without PIN check
- Response code depends on transaction result

**Account Without PIN Credentials:**
- Strategy throws exception (no encrypted_pin_block or pvv configured)
- Transaction aborted
- Response code: 96 (System malfunction)

**HSM Communication Error:**
- Strategy catches exception
- Transaction aborted
- Response code: 96 (System malfunction)

## Security Considerations

1. **Transport Security**:
   - Use HTTPS for HSM communication in production
   - Implement mutual TLS (mTLS) for enhanced security
   - Use VPN or private network for HSM connectivity

2. **Key Management**:
   - **TPK**: Must be properly managed and rotated regularly
   - **LMK**: Never leaves HSM, used for encrypted PIN block storage
   - **PVK**: Used for PVV generation, never leaves HSM
   - Implement key rotation policies (e.g., every 90 days)
   - Use HSM's key management capabilities

3. **PIN Storage**:
   - **NEVER** store clear text PINs
   - Encrypted PIN blocks: Stored as hex-encoded strings encrypted under LMK
   - PVV: Only 4-digit hash stored, more secure than encrypted PIN blocks
   - Consider migrating from encrypted PIN blocks to PVV for enhanced security

4. **Audit Logging**:
   - Log all PIN verification attempts with account, timestamp, and result
   - Log strategy selection (which method was used)
   - Never log clear PINs, PIN blocks, or PVVs in plain text
   - Monitor for suspicious patterns (repeated failures)

5. **Timeout Configuration**:
   - Set appropriate timeouts to prevent hanging connections
   - Default: connection timeout 5s, read timeout 10s
   - Adjust based on network conditions and HSM performance

6. **Error Handling**:
   - Never expose internal HSM errors to clients
   - Always return generic response code 96 for HSM errors
   - Log detailed errors internally for troubleshooting
   - Implement circuit breaker pattern for HSM communication

7. **Database Security**:
   - Encrypt database at rest
   - Use separate database user with minimal permissions
   - Regularly backup account credentials (encrypted_pin_block, pvv)
   - Consider hardware security modules for key storage

8. **Strategy Selection**:
   - Validate pin_verification_type before processing
   - Reject accounts with invalid configuration
   - Default to most secure method (PVV) for new accounts

## Monitoring

### Key Log Messages

**Strategy Initialization (on startup):**
```
INFO  - Initialized 2 PIN verification strategies: [ENCRYPTED_PIN_BLOCK, PVV]
```

**Strategy Selection:**
```
INFO  - Using ENCRYPTED_PIN_BLOCK verification strategy for account: {accountNumber}
INFO  - Using PVV verification strategy for account: {accountNumber}
```

**Encrypted PIN Block Verification:**
```
INFO  - Verifying PIN using encrypted PIN block translation for account: {accountNumber}
INFO  - PIN verification successful for account: {accountNumber}. TPK: {tpkKeyId}, LMK: {lmkKeyId}
WARN  - PIN verification failed for account: {accountNumber}. Message: {message}
ERROR - Error communicating with HSM for account: {accountNumber}. Error: {error}
```

**PVV Verification:**
```
INFO  - Verifying PIN using PVV method for account: {accountNumber}
INFO  - PVV verification successful for account: {accountNumber}. TPK: {tpkKeyId}, PVK: {pvkKeyId}
WARN  - PVV verification failed for account: {accountNumber}. Message: {message}
ERROR - Error communicating with HSM for account: {accountNumber}. Error: {error}
```

**General PIN Verification:**
```
INFO  - Verifying PIN for account: {accountNumber}
INFO  - PIN verified successfully for account: {accountNumber}
WARN  - Invalid PIN for account: {accountNumber}
DEBUG - No PIN block provided for account: {accountNumber}, skipping verification
```

### Metrics to Track

1. **Verification Success Rate**:
   - Total verifications per method (ENCRYPTED_PIN_BLOCK vs PVV)
   - Success vs failure ratio
   - Average response time per method

2. **Strategy Usage**:
   - Percentage of accounts using each method
   - Migration progress from ENCRYPTED_PIN_BLOCK to PVV

3. **HSM Performance**:
   - Average HSM response time
   - HSM timeout rate
   - Circuit breaker status

4. **Security Metrics**:
   - Failed verification attempts per account
   - Accounts with repeated failures (potential fraud)
   - HSM communication errors

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| Response code 96 | HSM not reachable | Check HSM URL and network connectivity |
| Response code 96 | HSM timeout | Increase timeout values in configuration |
| Response code 55 | Wrong PIN | Verify PIN encryption and TPK configuration |
| PIN verification skipped | Field 52 missing | Ensure ATM sends PIN block in field 52 |
| Exception: HSM endpoint not found | Wrong endpoint configuration | Check `hsm.pin.encrypted-pin-block.endpoint` and `hsm.pin.pvv.endpoint` |
| Exception: No strategy found | Invalid pin_verification_type | Verify account has valid verification type (ENCRYPTED_PIN_BLOCK or PVV) |
| Exception: No encrypted PIN block | Missing encrypted_pin_block | Account configured for ENCRYPTED_PIN_BLOCK but field is null |
| Exception: No PVV stored | Missing pvv | Account configured for PVV but field is null |
| Strategies not initialized | Configuration error | Check application.yml for correct HSM configuration |
| Wrong strategy selected | Database inconsistency | Verify account.pin_verification_type matches stored credentials |

### Common Issues and Solutions

**Issue: "Unsupported PIN verification type"**
```
ERROR - No strategy found for verification type: null
```
**Solution:**
- Ensure all accounts have a valid `pin_verification_type` value
- Run migration V4 to add the column with default value
- For new accounts, explicitly set verification type

**Issue: "No encrypted PIN block stored for account"**
```
ERROR - No encrypted PIN block stored for account: 1234567890
```
**Solution:**
- Account is configured for ENCRYPTED_PIN_BLOCK but `encrypted_pin_block` is null
- Either populate the field or change verification type to PVV
- Check if account needs PIN credentials setup

**Issue: "No PVV stored for account"**
```
ERROR - No PVV stored for account: 0987654321
```
**Solution:**
- Account is configured for PVV but `pvv` is null
- Either generate and store PVV or change verification type to ENCRYPTED_PIN_BLOCK
- Use HSM to generate PVV from PIN during setup

**Issue: Properties not loading correctly**
```
ERROR - Cannot find symbol: method getVerification()
```
**Solution:**
- Configuration structure changed from `verification.endpoint` to `encrypted-pin-block.endpoint` and `pvv.endpoint`
- Update application.yml and application-test.properties
- Rebuild application after configuration change

**Issue: HSM returns 404 Not Found**
```
ERROR - HSM endpoint not found: /api/hsm/pin/verify-with-pvv
```
**Solution:**
- HSM simulator might not have PVV endpoint implemented
- Update HSM simulator to latest version with PVV support
- Check HSM simulator API documentation

### Migration Path

**Migrating accounts from ENCRYPTED_PIN_BLOCK to PVV:**

1. Generate PVV from existing PIN (requires clear PIN or re-PIN process):
```sql
-- This requires HSM operation to generate PVV from PIN
-- Cannot generate PVV from encrypted PIN block directly
UPDATE accounts
SET pvv = '<HSM-generated-PVV>',
    pin_verification_type = 'PVV'
WHERE account_number = '1234567890';
```

2. For accounts without PVV, consider re-PIN process:
   - Customer enters new PIN at ATM
   - ATM sends PIN to HSM for PVV generation
   - Store PVV and update verification type

3. Gradual migration:
   - Keep existing accounts on ENCRYPTED_PIN_BLOCK
   - Set new accounts to PVV by default
   - Migrate accounts during next PIN change

## Summary

This application implements a flexible, extensible PIN verification system that supports multiple verification methods:

### Architecture Highlights

- **Strategy Pattern**: Allows easy addition of new verification methods without modifying existing code
- **Minimal jPOS Layer**: Participants only extract fields and delegate to business services
- **Reusable Business Logic**: `BankService.verifyPin()` can be called from any entry point
- **Type-Safe Configuration**: Uses enums for verification types and PIN formats
- **Comprehensive Error Handling**: Proper exception handling and response code mapping

### Supported Verification Methods

1. **Encrypted PIN Block (Translation Method)**
   - Uses two-key cryptography (TPK + LMK)
   - Compares encrypted PIN blocks
   - Suitable for traditional systems
   - Database stores: `encrypted_pin_block` (32-character hex string)

2. **PVV (PIN Verification Value)**
   - Uses one-way hash (ISO 9564 compliant)
   - Most secure: only 4-digit hash stored
   - Standard method in banking industry
   - Database stores: `pvv` (4-digit string)

### Implementation Files

**Domain & Entities:**
- `PinVerificationType.java` - Enum defining verification methods
- `Account.java` - Entity with PIN credentials and verification type

**Strategy Pattern:**
- `PinVerificationStrategy.java` - Strategy interface
- `EncryptedPinBlockVerificationStrategy.java` - Encrypted PIN block implementation
- `PvvVerificationStrategy.java` - PVV implementation

**Services:**
- `HsmService.java` - Strategy coordinator, selects appropriate strategy
- `BankService.java` - Entry point for PIN verification
- `HsmClient.java` - HTTP interface for HSM communication

**Configuration:**
- `HsmProperties.java` - Configuration properties for both endpoints
- `HsmConfig.java` - Spring configuration for RestClient

**DTOs:**
- `PinBlockVerificationRequest/Response.java` - For encrypted PIN block method
- `PvvVerificationRequest/Response.java` - For PVV method
- `PinFormat.java` - Enum for PIN block formats (ISO-0, ISO-1, ISO-3, ISO-4)

**Database:**
- `V3__add_encrypted_pin_block.sql` - Adds encrypted_pin_block column
- `V4__add_pvv_support.sql` - Adds pvv and pin_verification_type columns

### Key Benefits

✅ **Security**: PVV provides enhanced security with one-way hash
✅ **Flexibility**: Each account can use different verification method
✅ **Extensibility**: Easy to add new verification methods
✅ **Maintainability**: Clean separation of concerns
✅ **Testability**: Strategy pattern enables easy unit testing
✅ **Standards Compliance**: PVV method is ISO 9564 compliant
✅ **Backward Compatibility**: Existing accounts continue working with ENCRYPTED_PIN_BLOCK

### Production Considerations

Before deploying to production:

1. **Configure HTTPS** for HSM communication
2. **Implement mTLS** for enhanced security
3. **Set up monitoring** for verification metrics and HSM performance
4. **Configure alerts** for repeated failures and HSM errors
5. **Plan key rotation** schedule for TPK, LMK, and PVK
6. **Test failover** scenarios and circuit breaker configuration
7. **Verify compliance** with ISO 9564 and PCI DSS requirements
8. **Document procedures** for key management and incident response

For questions or issues, refer to the troubleshooting section or consult the HSM simulator documentation at https://github.com/artivisi/hsm-simulator
