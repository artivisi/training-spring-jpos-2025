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

- **01**: TPK (Terminal PIN Key) change request
- **02**: TSK (Terminal Session Key) change request
- Remaining 14 digits: Reserved (set to `00000000000000`)

**Examples:**
- TPK change: `0100000000000000`
- TSK change: `0200000000000000`

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
[IV (32 hex chars)] || [Ciphertext (64 hex chars)]
```

- **Total length**: 96 hexadecimal characters
- **IV**: 16 bytes (32 hex chars) - Initialization Vector for AES-128-CBC
- **Ciphertext**: 32 bytes (64 hex chars) - Encrypted new key (256-bit key)
- **Encryption algorithm**: AES-128-CBC with PKCS5 padding
- **Encryption key**: Current active master key (TPK or TSK)

**Example**:
```
A1B2C3D4E5F678901234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF
|------ IV (32 chars) ----------||---------- Ciphertext (64 chars) ----------|
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

### Step 1: Terminal Initiates Key Change

```
Terminal → Server: 0800 Request
{
  MTI: 0800
  Field 11: 000001
  Field 41: TRM-ISS001-ATM-001
  Field 53: 0100000000000000  (TPK change)
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
  Field 123: [96 hex chars - encrypted new key]
}
```

### Step 4: Terminal Key Installation

1. **Verify Response Code**: Check field 39 = "00"
2. **Verify MAC**: Verify response MAC using current TSK
3. **Extract Encrypted Key**: Parse field 123
4. **Decrypt New Key**:
   ```
   IV = first 32 hex chars of field 123
   Ciphertext = last 64 hex chars of field 123
   NewKey = AES-128-CBC-Decrypt(Ciphertext, CurrentKey, IV)
   ```
5. **Verify Checksum**:
   ```
   CalculatedChecksum = SHA-256(NewKey)[0:16]
   if (CalculatedChecksum != Field48) {
       ABORT - Checksum mismatch
   }
   ```
6. **Test New Key**: Perform test operation with new key
7. **Activate New Key**: Store as active, mark old key as expired
8. **Use New Key**: All subsequent transactions use the new key

### Step 5: Server Auto-Activation

**Important**: The server automatically activates the PENDING key when it detects the terminal using it:

1. **Terminal sends next transaction** (e.g., balance inquiry) with MAC using new key
2. **Server MAC verification**:
   - Tries ACTIVE key first
   - If ACTIVE key fails, tries all PENDING keys
   - If PENDING key succeeds, marks it for activation
3. **Server processes transaction** normally
4. **Server sends response** with MAC using the same key (PENDING)
5. **Server auto-activation** (after response sent):
   - Activates the PENDING key in database
   - Marks old ACTIVE key as EXPIRED
   - Confirms activation to HSM
6. **All subsequent transactions** use the newly activated key

**Grace Period Benefits**:
- Server accepts both old and new keys during transition
- Terminal can switch at any time
- No coordination needed between terminal and server
- Failed installations don't affect service

**No Explicit Confirmation Required**:
- Terminal does NOT need to send a separate confirmation message
- Using the new key IS the confirmation
- Server detects usage and activates automatically

---

## Implementation Guide

### Decryption Algorithm (Terminal Side)

```java
public byte[] decryptNewKey(String encryptedKeyHex, byte[] currentMasterKey) {
    // Parse field 123
    String ivHex = encryptedKeyHex.substring(0, 32);
    String ciphertextHex = encryptedKeyHex.substring(32, 96);

    byte[] iv = hexToBytes(ivHex);
    byte[] ciphertext = hexToBytes(ciphertextHex);

    // Decrypt using AES-128-CBC
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    SecretKeySpec keySpec = new SecretKeySpec(currentMasterKey, 0, 16, "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

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
        String ciphertextHex = encryptedKeyHex.substring(32, 96);

        byte[] iv = hexToBytes(ivHex);
        byte[] ciphertext = hexToBytes(ciphertextHex);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(currentKey, 0, 16, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

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

## Support

For implementation questions or issues:
- Review server logs for detailed error messages
- Check HSM connectivity and status
- Verify terminal configuration matches server expectations
- Contact technical support with STAN and timestamp for troubleshooting

---

**Document Version**: 1.0
**Last Updated**: 2025-10-31
**Status**: Production Ready
