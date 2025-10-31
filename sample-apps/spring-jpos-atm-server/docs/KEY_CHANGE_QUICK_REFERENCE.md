# Key Change Protocol - Quick Reference

## Request (0800)

```
MTI: 0800
Field 11: STAN (6 digits)
Field 41: Terminal ID (16 chars, left-padded)
Field 53: "01" + "00000000000000" (TPK)
          "02" + "00000000000000" (TSK)
Field 64: MAC (8 bytes)
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
Field 123: Encrypted new key (96 hex chars)
```

## Field 123 Structure

```
[IV: 32 hex] + [Ciphertext: 64 hex] = 96 hex chars total

Example:
A1B2C3D4E5F678901234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF
|------------ IV (32) -----------||------------------ Ciphertext (64) ------------------|
```

## Decryption Steps

```java
// 1. Extract IV and ciphertext
String ivHex = field123.substring(0, 32);
String ciphertextHex = field123.substring(32, 96);
byte[] iv = hexToBytes(ivHex);
byte[] ciphertext = hexToBytes(ciphertextHex);

// 2. Decrypt using AES-128-CBC
Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
SecretKeySpec keySpec = new SecretKeySpec(currentKey, 0, 16, "AES");
IvParameterSpec ivSpec = new IvParameterSpec(iv);
cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
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
1. Send 0800 request
2. Receive 0810 response
3. Verify MAC on response
4. Check response code = "00"
5. Decrypt field 123
6. Verify checksum (field 48)
7. Test new key
8. Activate new key locally
9. Mark old key as expired
10. Use new key in next transaction
11. Server auto-activates when it detects new key usage
```

## Server Auto-Activation

**Important**: The server automatically activates PENDING keys:

- Terminal uses new key → Server detects via MAC verification
- Server tries ACTIVE key first, then PENDING keys
- If PENDING key succeeds → Server auto-activates after sending response
- Server confirms to HSM automatically
- **No explicit confirmation message needed from terminal**

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
Field 123: [96 hex chars]
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
