# Key Change Protocol - Quick Reference

## Request Operations

### Key Request (operations 01/02)

```
MTI: 0800
Field 11: STAN (6 digits)
Field 41: Terminal ID
Field 42: Card Acceptor ID
Field 53: "01" + "00000000000000" (TPK request)
          "02" + "00000000000000" (TSK request)
Field 64: MAC (8 bytes)
```

### Key Confirmation (operations 03/04)

```
MTI: 0800
Field 11: STAN (6 digits)
Field 41: Terminal ID
Field 42: Card Acceptor ID
Field 53: "03" + "00000000000000" (TPK confirmation)
          "04" + "00000000000000" (TSK confirmation)
Field 64: MAC (8 bytes using NEW key)
```

### Key Failure (operations 05/06)

```
MTI: 0800
Field 11: STAN (6 digits)
Field 41: Terminal ID
Field 42: Card Acceptor ID
Field 48: Failure reason (text)
Field 53: "05" + "00000000000000" (TPK failure)
          "06" + "00000000000000" (TSK failure)
Field 64: MAC (8 bytes using OLD key)
```

## Response (0810)

```
MTI: 0810
Field 11: STAN (echoed)
Field 39: "00" (success) or error code
Field 41: Terminal ID (echoed)
Field 48: Key checksum (16 hex chars)
Field 53: Operation code (echoed)
Field 64: MAC (8 bytes)
Field 123: Encrypted new key (128 hex chars)
```

## Field 123 Structure

```
[IV: 32 hex] + [Ciphertext: 96 hex] = 128 hex chars total

Example:
A1B2C3D4E5F678901234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF
|------------ IV (32) -----------||----------------------------------- Ciphertext (96) -----------------------------------|
```

**Note**: Ciphertext is 48 bytes (96 hex chars) due to PKCS5 padding:
- Plaintext: 32 bytes (new key)
- PKCS5 adds: 16 bytes (full block)
- Total ciphertext: 48 bytes

## Decryption Steps

```java
// 1. Extract IV and ciphertext
String ivHex = field123.substring(0, 32);
String ciphertextHex = field123.substring(32, 128);
byte[] iv = hexToBytes(ivHex);
byte[] ciphertext = hexToBytes(ciphertextHex);

// 2. Decrypt using AES-128-CBC with PKCS5Padding
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
SecretKeySpec keySpec = new SecretKeySpec(currentKey, 0, 16, "AES");
IvParameterSpec ivSpec = new IvParameterSpec(iv);
cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
// PKCS5Padding automatically removes padding, returns 32-byte key
byte[] newKey = cipher.doFinal(ciphertext);

// 3. Verify checksum (field 48)
MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
byte[] hash = sha256.digest(newKey);
String checksum = bytesToHex(hash).substring(0, 16);
if (!checksum.equalsIgnoreCase(field48)) {
    throw new SecurityException("Checksum mismatch");
}
```

## Response Codes

| Code | Meaning | Action |
|------|---------|--------|
| 00 | Success | Install new key |
| 30 | Format error | Check field 53 |
| 96 | System error | Retry later |

## Common Errors

### Checksum Mismatch
- Verify using correct current key
- Check field 123 not corrupted
- Request new key if persistent

### Decryption Fails
- Confirm current key version
- Verify IV extraction (chars 0-31)
- Check ciphertext extraction (chars 32-95)

### Response Code 30
- Verify field 53 format
- TPK: `0100000000000000`
- TSK: `0200000000000000`

## Key Activation Flow

```
1. Send 0800 key request (operation 01/02)
2. Receive 0810 response with encrypted key
3. Verify MAC on response
4. Check response code = "00"
5. Decrypt field 123
6. Verify checksum (field 48)
7. Test new key locally
8. Store new key as PENDING locally
9. Send 0800 confirmation (operation 03/04) using NEW key
10. Receive 0810 confirmation response
11. Server activates PENDING key → ACTIVE
12. Terminal activates new key, expires old key
```

## Explicit Confirmation Required

**Important**: The server requires explicit confirmation:

- Terminal sends confirmation message (operation 03/04) using NEW key
- Server verifies MAC with PENDING key
- Server activates PENDING key → ACTIVE after sending response
- Server confirms to HSM automatically
- **Explicit confirmation message IS required from terminal**

## Failure Handling

If installation fails:
- Terminal sends failure notification (operation 05/06) using OLD key
- Terminal includes failure reason in field 48
- Server removes PENDING key from database
- Server notifies HSM of failure
- Old ACTIVE key remains unchanged

## Security Checklist

- [ ] Always verify response MAC
- [ ] Always verify checksum before activation
- [ ] Never activate key with failed checksum
- [ ] Store keys in secure memory
- [ ] Maintain grace period for old key
- [ ] Log all key change events
- [ ] Increment key version number

## Test Message Examples

### TPK Change Request
```
0800
Field 11: 000001
Field 41: TRM-ISS001-ATM-001 (spaces padded to 16)
Field 53: 0100000000000000
Field 64: [8-byte MAC]
```

### Success Response
```
0810
Field 11: 000001
Field 39: 00
Field 41: TRM-ISS001-ATM-001
Field 48: 3A5F9B2C8D1E4F7A
Field 53: 0100000000000000
Field 64: [8-byte MAC]
Field 123: [128 hex chars]
```

### Error Response
```
0810
Field 11: 000001
Field 39: 96
Field 41: TRM-ISS001-ATM-001
Field 53: 0100000000000000
Field 64: [8-byte MAC]
(No field 123 or 48)
```

## Troubleshooting Commands

```bash
# Check server logs
tail -f logs/application.log | grep -i "key.*change"

# Verify terminal ID in database
SELECT * FROM crypto_keys WHERE terminal_id = 'TRM-ISS001-ATM-001';

# Check active keys
SELECT terminal_id, key_type, key_version, status
FROM crypto_keys
WHERE status = 'ACTIVE';

# View key rotation history
SELECT terminal_id, key_type, key_version, status, effective_from
FROM crypto_keys
ORDER BY effective_from DESC
LIMIT 20;
```
