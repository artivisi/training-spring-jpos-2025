# ISO-8583 Key Change Protocol Documentation

## Overview

This document describes the protocol for terminal-initiated cryptographic key rotation using ISO-8583 network management messages. The protocol allows ATM terminals to request new encryption keys (TPK or TSK) from the server.

## Protocol Summary

- **Request MTI**: 0800 (Network Management Request)
- **Response MTI**: 0810 (Network Management Response)
- **Key Transport**: New key is encrypted under the current active key
- **Key Types Supported**: TPK (Terminal PIN Key), TSK (Terminal Session Key)

---

## Request Message (0800)

### Required Fields

| Field | Name | Format | Length | Description | Example |
|-------|------|--------|--------|-------------|---------|
| MTI | Message Type Indicator | N | 4 | Network management request | `0800` |
| 11 | STAN | N | 6 | System Trace Audit Number | `000001` |
| 41 | Card Acceptor Terminal ID | ANS | 16 | Terminal identifier | `TRM-ISS001-ATM-001` (left-padded with spaces) |
| 53 | Security Related Control Information | N | 16 | Key change operation code | See below |
| 64 | MAC | B | 8 | Message Authentication Code | See MAC generation |

### Field 53 Format

Field 53 contains a 16-digit numeric value where the first 2 digits indicate the operation:

**Key Request:**
- **01**: TPK (Terminal PIN Key) key request - server sends encrypted new key
- **02**: TSK (Terminal Session Key) key request - server sends encrypted new key

**Key Confirmation:**
- **03**: TPK installation confirmed - terminal confirms successful installation
- **04**: TSK installation confirmed - terminal confirms successful installation

**Key Failure:**
- **05**: TPK installation failed - terminal reports installation failure
- **06**: TSK installation failed - terminal reports installation failure

Remaining 14 digits: Reserved (set to `00000000000000`)

**Examples:**
- TPK request: `0100000000000000`
- TSK request: `0200000000000000`
- TPK confirm: `0300000000000000`
- TSK confirm: `0400000000000000`
- TPK failure: `0500000000000000`
- TSK failure: `0600000000000000`

### Sample Request Message

```
MTI: 0800
Field 11: 000001
Field 41: TRM-ISS001-ATM-001 (padded to 16 chars)
Field 53: 0100000000000000
Field 64: [8-byte MAC]
```

---

## Response Message (0810)

### Response Fields

| Field | Name | Format | Length | Description |
|-------|------|--------|--------|-------------|
| MTI | Message Type Indicator | N | 4 | Network management response |
| 11 | STAN | N | 6 | Echoed from request |
| 39 | Response Code | AN | 2 | Transaction result |
| 41 | Card Acceptor Terminal ID | ANS | 16 | Echoed from request |
| 48 | Additional Data - Private | ANS | var | Key checksum (16 hex chars) |
| 53 | Security Related Control Information | N | 16 | Echoed from request |
| 64 | MAC | B | 8 | Message Authentication Code |
| 123 | Encrypted New Key | ANS | var | New key encrypted under current key |

### Field 39 Response Codes

| Code | Meaning | Action |
|------|---------|--------|
| 00 | Approved | Proceed with key installation |
| 30 | Format error | Check field 53 format |
| 96 | System malfunction | Retry later or contact support |

### Field 48 Format (Key Checksum)

- **Format**: 16 hexadecimal characters (uppercase)
- **Algorithm**: First 16 characters of SHA-256 hash of the decrypted key
- **Purpose**: Verify key integrity after decryption
- **Example**: `3A5F9B2C8D1E4F7A`

### Field 123 Format (Encrypted New Key)

The encrypted new key is returned in hexadecimal format with the following structure:

```
[IV (32 hex chars)] || [Ciphertext (96 hex chars)]
```

- **Total length**: 128 hexadecimal characters (64 bytes)
- **IV**: 16 bytes (32 hex chars) - Initialization Vector for AES-128-CBC
- **Ciphertext**: 48 bytes (96 hex chars) - Encrypted new key (32-byte key + 16-byte PKCS5 padding)
- **Encryption algorithm**: AES-128-CBC with PKCS5 padding
- **Encryption key**: Current active master key (TPK or TSK)

**Example**:
```
A1B2C3D4E5F678901234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF
|------ IV (32 chars) ----------||-------------------------------- Ciphertext (96 chars) --------------------------------|
```

### Sample Response Message (Success)

```
MTI: 0810
Field 11: 000001
Field 39: 00
Field 41: TRM-ISS001-ATM-001
Field 48: 3A5F9B2C8D1E4F7A
Field 53: 0100000000000000
Field 64: [8-byte MAC]
Field 123: A1B2C3D4E5F678901234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF
```

---

## Complete Key Change Flow

### Step 1: Terminal Requests New Key (operation 01/02)

```
Terminal → Server: 0800 Request
{
  MTI: 0800
  Field 11: 000001
  Field 41: ATM-001
  Field 42: TRM-ISS001
  Field 53: 0100000000000000  (TPK request)
  Field 64: [MAC using current TSK]
}
```

### Step 2: Server Processing

1. **MAC Verification**: Server verifies MAC using current TSK
2. **Terminal Authentication**: Validates terminal ID from field 41
3. **HSM Request**: Server requests new key from HSM
4. **Key Encryption**: HSM encrypts new key under current key
5. **Checksum Generation**: Server calculates SHA-256 checksum

### Step 3: Server Response

```
Server → Terminal: 0810 Response
{
  MTI: 0810
  Field 11: 000001
  Field 39: 00
  Field 41: TRM-ISS001-ATM-001
  Field 48: 3A5F9B2C8D1E4F7A  (checksum)
  Field 53: 0100000000000000
  Field 64: [MAC using current TSK]
  Field 123: [128 hex chars - encrypted new key]
}
```

### Step 4: Terminal Key Installation

1. **Verify Response Code**: Check field 39 = "00"
2. **Verify MAC**: Verify response MAC using current TSK
3. **Extract Encrypted Key**: Parse field 123
4. **Decrypt New Key**:
   ```
   IV = first 32 hex chars of field 123
   Ciphertext = remaining 96 hex chars of field 123
   NewKey = AES-128-CBC-Decrypt(Ciphertext, CurrentKey, IV)
   ```
5. **Verify Checksum**:
   ```
   CalculatedChecksum = SHA-256(NewKey)[0:16]
   if (CalculatedChecksum != Field48) {
       ABORT - Send failure notification (operation 05/06)
   }
   ```
6. **Test New Key**: Perform local test with new key
7. **Store New Key**: Store as PENDING locally, keep old key as backup
8. **Send Confirmation or Failure**: Proceed to step 5 or step 6

### Step 5: Terminal Confirms Successful Installation (operation 03/04)

**Important**: Terminal MUST send explicit confirmation after successful installation.

```
Terminal → Server: 0800 Confirmation
{
  MTI: 0800
  Field 11: 000002
  Field 41: ATM-001
  Field 42: TRM-ISS001
  Field 53: 0300000000000000  (TPK confirmation)
  Field 64: [MAC using NEW TPK key]
}
```

**Server Processing**:
1. **MAC Verification**: Server tries ACTIVE key first, then PENDING keys
2. **PENDING Key Detection**: Server detects MAC verified with PENDING key
3. **Mark Confirmation**: Server marks KEY_CHANGE_CONFIRMED in context
4. **Send Response**: Server sends 0810 response
5. **Activate Key** (after response sent):
   - Server activates PENDING key → ACTIVE
   - Server expires old ACTIVE key → EXPIRED
   - Server confirms to HSM

**Response**:
```
Server → Terminal: 0810 Response
{
  MTI: 0810
  Field 11: 000002
  Field 39: 00
  Field 41: ATM-001
  Field 64: [MAC using NEW TPK key]
}
```

### Step 6: Terminal Reports Installation Failure (operation 05/06)

If terminal fails to install key (decryption error, checksum mismatch, test failure):

```
Terminal → Server: 0800 Failure
{
  MTI: 0800
  Field 11: 000002
  Field 41: ATM-001
  Field 42: TRM-ISS001
  Field 48: "Checksum mismatch"  (failure reason)
  Field 53: 0500000000000000  (TPK failure)
  Field 64: [MAC using OLD/current TSK]
}
```

**Server Processing**:
1. **Log Failure**: Server logs critical security event
2. **Remove PENDING Key**: Server deletes PENDING key from database
3. **Notify HSM**: Server reports failure to HSM
4. **Keep Old Key**: Old ACTIVE key remains active
5. **Send Acknowledgment**: Server confirms receipt

**Response**:
```
Server → Terminal: 0810 Response
{
  MTI: 0810
  Field 11: 000002
  Field 39: 00  (acknowledgment)
  Field 41: ATM-001
  Field 64: [MAC using OLD TSK]
}
```

**Grace Period Benefits**:
- Server accepts both old and new keys during transition
- Terminal can test new key before full activation
- Failed installations logged and cleaned up
- Old key remains active if installation fails

---

## Implementation Guide

### Decryption Algorithm (Terminal Side)

```java
public byte[] decryptNewKey(String encryptedKeyHex, byte[] currentMasterKey) {
    // Parse field 123
    String ivHex = encryptedKeyHex.substring(0, 32);
    String ciphertextHex = encryptedKeyHex.substring(32, 128);

    byte[] iv = hexToBytes(ivHex);
    byte[] ciphertext = hexToBytes(ciphertextHex);

    // Decrypt using AES-128-CBC with PKCS5Padding
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    SecretKeySpec keySpec = new SecretKeySpec(currentMasterKey, 0, 16, "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

    // PKCS5Padding automatically removes padding, returns 32-byte key
    return cipher.doFinal(ciphertext);
}
```

### Checksum Verification (Terminal Side)

```java
public boolean verifyChecksum(byte[] decryptedKey, String checksumHex) {
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    byte[] hash = sha256.digest(decryptedKey);

    // Take first 8 bytes of hash
    String calculatedChecksum = bytesToHex(hash).substring(0, 16);

    return calculatedChecksum.equalsIgnoreCase(checksumHex);
}
```

### MAC Generation

For MAC generation on both request and response, refer to the MAC specification document. Key points:

- **Algorithm**: AES-CMAC
- **Key**: Current active TSK (Terminal Session Key)
- **Data**: All fields except field 64 itself
- **Length**: 8 bytes

---

## Error Handling

### Server Returns Error Response

```
Server → Terminal: 0810 Response (Error)
{
  MTI: 0810
  Field 11: 000001
  Field 39: 96  (System malfunction)
  Field 41: TRM-ISS001-ATM-001
  Field 53: 0100000000000000
  Field 64: [MAC]
}
```

**Terminal Actions:**
- Log error details
- Retry after backoff period (recommended: exponential backoff)
- Alert operator if retries exceed threshold
- Continue using current key until rotation succeeds

### Checksum Verification Fails

**Terminal Actions:**
1. Log security event
2. **DO NOT** install the new key
3. Discard encrypted key data
4. Alert security administrator
5. Retry key change request
6. If repeated failures, contact server administrator

### Decryption Fails

**Possible Causes:**
- Wrong current key used for decryption
- Corrupted transmission
- Server/HSM malfunction

**Terminal Actions:**
1. Verify using correct current active key
2. Retry decryption with PENDING key (if in grace period)
3. Log error and alert operator
4. Do not activate new key

---

## Security Considerations

### 1. Key Storage
- Store new key in secure, tamper-resistant memory
- Encrypt key storage if possible
- Clear old key from memory after grace period

### 2. Transmission Security
- Always verify MAC on response
- Reject responses with invalid MAC
- Use secure channel (TLS) for ISO-8583 transport

### 3. Rollback Protection
- Store key version numbers
- Reject keys with version numbers <= current version
- Maintain audit log of all key changes

### 4. Grace Period Handling
- During grace period, support both old and new keys for incoming transactions
- Always use new key for outgoing transactions after activation
- Typical grace period: 24 hours

### 5. Key Lifecycle
```
PENDING → ACTIVE → EXPIRED
```

- **PENDING**: New key received but not yet activated
- **ACTIVE**: Currently in use for all operations
- **EXPIRED**: Old key, keep for grace period then destroy

---

## Testing Checklist

### Pre-Production Testing

- [ ] Successful TPK rotation
- [ ] Successful TSK rotation
- [ ] Checksum verification with valid key
- [ ] Checksum verification with invalid key (should fail)
- [ ] Response code 30 handling (format error)
- [ ] Response code 96 handling (system error)
- [ ] MAC verification on response
- [ ] Decryption with correct key
- [ ] Decryption with wrong key (should fail)
- [ ] Key activation after successful verification
- [ ] Transaction processing with new key
- [ ] Grace period handling (old and new keys)

### Production Monitoring

- Monitor key rotation success rate
- Alert on repeated failures
- Track key version numbers
- Audit log all key changes
- Monitor for checksum verification failures

---

## Troubleshooting

### Problem: Response Code 30

**Cause**: Invalid field 53 format

**Solution**:
- Verify field 53 = `0100000000000000` for TPK or `0200000000000000` for TSK
- Ensure field is exactly 16 digits
- Check no extra spaces or padding

### Problem: Checksum Mismatch

**Cause**: Decrypted key doesn't match checksum in field 48

**Possible Issues**:
1. Wrong decryption key used
2. Corrupted field 123 during transmission
3. HSM/server malfunction

**Solution**:
1. Verify using correct current active key
2. Request retransmission
3. Contact server administrator if persistent

### Problem: Decryption Fails

**Cause**: Cannot decrypt field 123

**Possible Issues**:
1. Using wrong current key
2. Incorrect IV extraction
3. Corrupted ciphertext

**Solution**:
1. Verify current key version and value
2. Check field 123 parsing (IV = chars 0-31, ciphertext = chars 32-95)
3. Verify AES-128-CBC with PKCS5Padding settings

### Problem: MAC Verification Fails on Response

**Cause**: Response MAC invalid

**Action**:
- **CRITICAL SECURITY EVENT**
- Reject response immediately
- Do not process field 123
- Log security alert
- Contact security team

---

## Example Code (Complete Flow)

```java
public class KeyChangeHandler {

    public void initiateKeyChange(KeyType keyType) throws Exception {
        // 1. Build request
        ISOMsg request = new ISOMsg();
        request.setMTI("0800");
        request.set(11, generateSTAN());
        request.set(41, String.format("%-16s", terminalId));

        String operation = keyType == KeyType.TPK ? "01" : "02";
        request.set(53, operation + "00000000000000");

        // 2. Generate MAC
        byte[] mac = generateMAC(request, currentTSK);
        request.set(64, mac);

        // 3. Send request
        ISOMsg response = sendAndReceive(request);

        // 4. Verify MAC on response
        if (!verifyMAC(response, currentTSK)) {
            throw new SecurityException("Invalid response MAC");
        }

        // 5. Check response code
        String responseCode = response.getString(39);
        if (!"00".equals(responseCode)) {
            throw new Exception("Key change failed: " + responseCode);
        }

        // 6. Extract encrypted key and checksum
        String encryptedKeyHex = response.getString(123);
        String checksumHex = response.getString(48);

        // 7. Decrypt new key
        byte[] currentKey = keyType == KeyType.TPK ? currentTPK : currentTSK;
        byte[] newKey = decryptNewKey(encryptedKeyHex, currentKey);

        // 8. Verify checksum
        if (!verifyChecksum(newKey, checksumHex)) {
            throw new SecurityException("Checksum verification failed");
        }

        // 9. Test new key
        boolean testPassed = testKey(newKey, keyType);
        if (!testPassed) {
            throw new Exception("New key test failed");
        }

        // 10. Activate new key
        activateKey(newKey, keyType);

        log.info("Key change completed successfully: {}", keyType);
    }

    private byte[] decryptNewKey(String encryptedKeyHex, byte[] currentKey)
            throws Exception {
        String ivHex = encryptedKeyHex.substring(0, 32);
        String ciphertextHex = encryptedKeyHex.substring(32, 128);

        byte[] iv = hexToBytes(ivHex);
        byte[] ciphertext = hexToBytes(ciphertextHex);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(currentKey, 0, 16, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        // PKCS5Padding automatically removes padding, returns 32-byte key
        return cipher.doFinal(ciphertext);
    }

    private boolean verifyChecksum(byte[] key, String checksumHex)
            throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(key);
        String calculated = bytesToHex(hash).substring(0, 16);
        return calculated.equalsIgnoreCase(checksumHex);
    }
}
```

---

## Testing Guide

### End-to-End Key Change Testing

This section describes how to test the complete key change operation across all components (ATM, Server, HSM).

#### Prerequisites

- ATM simulator running on port 7070
- ATM server running on port 22222
- HSM simulator running on port 8080
- All components connected and healthy

#### Test Procedure

**Step 1: Verify System Before Key Change**

Test that all components are working with current keys:

```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{
    "pan": "4111111111111111",
    "pin": "1234"
  }'
```

**Expected Result**:
```json
{
  "success": true,
  "balance": 1000.00,
  "responseCode": "00",
  "message": "Balance inquiry successful"
}
```

**What to verify**:
- ATM logs show: "MAC verification successful"
- Server logs show: "MAC verified with ACTIVE TSK key"
- HSM logs show: "PIN verification successful"
- No errors in any component

---

**Step 2: Execute Key Change**

Initiate key rotation for TPK (Terminal PIN Key):

```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "TPK"
  }'
```

**Expected Result**:
```json
{
  "success": true,
  "keyType": "TPK",
  "keyId": "uuid-here",
  "checkValue": "883F61016",
  "message": "Key changed successfully via ISO-8583"
}
```

**What happens internally**:

1. **ATM → Server** (0800 request, operation 01):
   ```
   MTI: 0800
   Field 53: 0100000000000000 (TPK request)
   Field 64: MAC using OLD TSK
   ```

2. **Server → ATM** (0810 response):
   ```
   Field 39: 00 (success)
   Field 48: 883F61016 (checksum)
   Field 123: [128 hex chars - encrypted new TPK]
   Field 64: MAC using OLD TSK
   ```

3. **ATM processes**:
   - Decrypts new TPK using key derivation
   - Verifies SHA-256 checksum
   - Stores new TPK as ACTIVE locally
   - Reloads key into runtime memory

4. **ATM → Server** (0800 confirmation, operation 03):
   ```
   MTI: 0800
   Field 53: 0300000000000000 (TPK confirmation)
   Field 64: MAC using NEW TSK
   ```

5. **Server activates**:
   - Detects MAC verified with PENDING TSK
   - Activates PENDING TPK → ACTIVE
   - Expires old ACTIVE TPK → EXPIRED
   - Notifies HSM of activation

**Verify in logs**:

ATM logs should show:
```
INFO: Initiating key change for key type: TPK
INFO: Key change approved by server, processing encrypted key
INFO: Checksum verification successful: 883F61016
INFO: Key change completed successfully for TPK
INFO: Key installation successful, sending confirmation to server
INFO: Server acknowledged key installation confirmation: response code 00
```

Server logs should show:
```
INFO: Processing key change request: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Stored new TPK key as PENDING: version=2
INFO: Processing key confirmation: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Explicit confirmation received for TPK key installation
INFO: Successfully activated TPK key version 2
INFO: Successfully confirmed TPK key activation to HSM
```

HSM logs should show:
```
INFO: Generated new TPK for terminal: TRM-ISS001-ATM-001
INFO: Encrypted new key under current TPK
INFO: Key activation confirmed for terminal: TRM-ISS001-ATM-001, keyType: TPK
```

---

**Step 3: Verify System After Key Change**

Test that all components are working with NEW keys:

```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{
    "pan": "4111111111111111",
    "pin": "1234"
  }'
```

**Expected Result**:
```json
{
  "success": true,
  "balance": 1000.00,
  "responseCode": "00",
  "message": "Balance inquiry successful"
}
```

**What to verify**:
- ATM logs show: "Using NEW TPK for PIN encryption"
- ATM logs show: "MAC verification successful" (using NEW TSK from confirmation)
- Server logs show: "MAC verified with ACTIVE TSK key version: 1" (still old TSK - only TPK changed)
- HSM logs show: "PIN decrypted successfully using NEW TPK"
- HSM logs show: "PIN verification successful"
- No BadPaddingException errors
- No MAC verification failures

**Critical Success Indicators**:
- ✅ Balance inquiry succeeds with response code 00
- ✅ PIN verification succeeds (proves new TPK works end-to-end)
- ✅ MAC verification succeeds (proves TSK still works)
- ✅ No errors across ATM, Server, HSM logs

---

**Step 4: Test TSK Key Change (Optional)**

Repeat steps 1-3 for TSK (Terminal Session Key):

```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "TSK"
  }'
```

After TSK change, the confirmation message itself uses the NEW TSK for MAC generation, demonstrating the new key is working.

---

### Troubleshooting Test Failures

**Failure at Step 1 (Before Key Change)**:
- **Symptom**: Balance inquiry fails
- **Action**: Fix connectivity/configuration issues before attempting key change
- **Check**: Network connectivity, database connections, key configurations

**Failure at Step 2 (During Key Change)**:
- **Symptom**: Key change returns success: false
- **Check Server logs**: Look for errors in key generation, encryption, or HSM communication
- **Check ATM logs**: Look for decryption errors, checksum mismatches
- **Common causes**:
  - Checksum mismatch (server/client using different algorithms)
  - Decryption failure (key derivation mismatch)
  - HSM communication failure

**Failure at Step 3 (After Key Change)**:
- **Symptom**: Balance inquiry fails after successful key change
- **Error**: BadPaddingException in HSM logs
- **Root cause**: HSM still using old TPK, activation failed
- **Check**:
  - Server logs: Did KeyActivationParticipant activate the key?
  - HSM logs: Did HSM receive activation confirmation?
  - Database: Check crypto_keys table, is new key ACTIVE?
- **Resolution**:
  - Check explicit confirmation was sent (operation 03/04)
  - Verify HSM endpoint is reachable from server
  - Manually activate key if auto-activation failed

**Symptom**: "MAC verification failed" in server logs
- **Cause**: Terminal using new TSK but server hasn't activated it
- **Check**: KeyActivationParticipant logs, should show activation
- **Resolution**: Ensure confirmation message (operation 03/04) was sent

---

## Support

For implementation questions or issues:
- Review server logs for detailed error messages
- Check HSM connectivity and status
- Verify terminal configuration matches server expectations
- Contact technical support with STAN and timestamp for troubleshooting

---

**Document Version**: 1.1
**Last Updated**: 2025-10-31
**Status**: Production Ready
