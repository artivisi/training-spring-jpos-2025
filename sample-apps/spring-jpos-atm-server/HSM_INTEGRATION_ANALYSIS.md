# HSM Simulator Integration Analysis

## Executive Summary

The current ATM server implementation **can perform PIN verification using both methods** to the artivisi HSM simulator with **one minor issue** that needs fixing.

### Compatibility Status

| Method | Status | Issue |
|--------|--------|-------|
| **Method A** (PIN Block Translation) | ⚠️ **Partially Compatible** | Extra field `encryptionAlgorithm` not expected by HSM |
| **Method B** (PVV Verification) | ✅ **Fully Compatible** | Perfect match |

## Detailed Analysis

### HSM Simulator API Specification

**Source:** https://github.com/artivisi/hsm-simulator/blob/main/docs/API.md

#### Method A: POST /api/hsm/pin/verify-with-translation

**Expected Request:**
```json
{
  "pinBlockUnderLMK": "string (required)",
  "pinBlockUnderTPK": "string (required)",
  "terminalId": "string (required)",
  "pan": "string (required)",
  "pinFormat": "string (optional, default: ISO-0)"
}
```

**Expected Response:**
```json
{
  "valid": "boolean",
  "message": "string",
  "terminalId": "string",
  "pan": "string",
  "pinFormat": "string",
  "lmkKeyId": "string",
  "tpkKeyId": "string"
}
```

#### Method B: POST /api/hsm/pin/verify-with-pvv

**Expected Request:**
```json
{
  "pinBlockUnderTPK": "string (required)",
  "storedPVV": "string (required)",
  "terminalId": "string (required)",
  "pan": "string (required)",
  "pinFormat": "string (optional, default: ISO-0)"
}
```

**Expected Response:**
```json
{
  "valid": "boolean",
  "message": "string",
  "method": "string",
  "terminalId": "string",
  "pan": "string",
  "pinFormat": "string",
  "tpkKeyId": "string",
  "pvkKeyId": "string",
  "storedPVV": "string"
}
```

### Current Implementation

#### Configuration (`application.yml`)

```yaml
hsm:
  url: http://localhost:8080
  pin:
    encrypted-pin-block:
      endpoint: /api/hsm/pin/verify-with-translation  ✅ Correct
    pvv:
      endpoint: /api/hsm/pin/verify-with-pvv          ✅ Correct
    terminal-id: ATM00001
    format: ISO_0
    encryption-algorithm: AES_128                     ⚠️ Extra field
```

#### Method A Implementation

**File:** `EncryptedPinBlockVerificationStrategy.java:36-43`

**Current Request:**
```java
PinBlockVerificationRequest.builder()
    .pinBlockUnderTPK(pinBlockFromTerminal)           ✅ Matches
    .pinBlockUnderLMK(storedPinBlock)                 ✅ Matches
    .terminalId(hsmProperties.getPin().getTerminalId()) ✅ Matches
    .pan(pan)                                         ✅ Matches
    .pinFormat(hsmProperties.getPin().getFormat())    ✅ Matches
    .encryptionAlgorithm(...)                         ❌ EXTRA FIELD
    .build();
```

**Issue:** The `encryptionAlgorithm` field is sent but not expected by HSM API.

**Impact:**
- HSM may ignore the extra field (best case)
- HSM may reject the request with 400 Bad Request (worst case)
- Depends on HSM's JSON deserialization strictness

#### Method B Implementation

**File:** `PvvVerificationStrategy.java:37-43`

**Current Request:**
```java
PvvVerificationRequest.builder()
    .pinBlockUnderTPK(pinBlockFromTerminal)           ✅ Matches
    .storedPVV(storedPvv)                             ✅ Matches
    .terminalId(hsmProperties.getPin().getTerminalId()) ✅ Matches
    .pan(pan)                                         ✅ Matches
    .pinFormat(hsmProperties.getPin().getFormat())    ✅ Matches
    .build();
```

**Status:** ✅ **Perfect match - no issues**

### Encryption Algorithm Support

#### HSM Simulator

From the API documentation:
- **PIN Encryption**: AES (unspecified key length, likely AES-128 or AES-256)
- **MAC Operations**:
  - AES-CMAC (64-bit, 128-bit, 256-bit)
  - HMAC-SHA256 (64-bit, 256-bit, full)
- **Key Sizes**: 128-bit, 192-bit, 256-bit supported

**Encryption Mode:** AES-CBC with PKCS5 padding

#### ATM Server Implementation

- **PIN Encryption**: Supports both 3DES (field 52) and AES-128 (field 123)
- **MAC Operations**: AES-CMAC and HMAC-SHA256 (truncated)
- **Default Configuration**: AES-128 for PIN, AES-CMAC for MAC

**Conclusion:** ✅ **Encryption algorithms are compatible**

### Field Mapping: ISO-8583 vs HSM

| ISO-8583 Field | Usage | ATM Server | HSM Expectation |
|----------------|-------|------------|-----------------|
| Field 2 (PAN) | Account number | ✅ Sent | ✅ Required |
| Field 52 | 3DES PIN block (8 bytes) | ✅ Supported | ✅ Compatible |
| Field 123 | AES-128 PIN block (16 bytes) | ✅ Supported | ✅ Compatible (if hex-encoded) |
| Field 64 | MAC (16 bytes) | ✅ Supported | Not validated by HSM |

**Note:** The HSM simulator accepts PIN blocks as hex-encoded strings, regardless of source field.

## Issues and Recommendations

### Issue 1: Extra `encryptionAlgorithm` Field

**Severity:** ⚠️ **Medium**

**Location:** `PinBlockVerificationRequest.java` and `EncryptedPinBlockVerificationStrategy.java`

**Problem:** The DTO includes a field not expected by HSM API.

**Options:**

#### Option A: Remove from DTO (Recommended)
```java
// PinBlockVerificationRequest.java
// Remove this field:
// private PinEncryptionAlgorithm encryptionAlgorithm; ❌

// Strategy will still work because HSM determines algorithm
// from the PIN block format and length
```

**Pros:**
- Clean match with HSM API
- No serialization issues

**Cons:**
- Lose ability to track which algorithm was used internally

#### Option B: Use @JsonIgnore
```java
@JsonIgnore
private PinEncryptionAlgorithm encryptionAlgorithm;
```

**Pros:**
- Keep field for internal use
- Not sent to HSM

**Cons:**
- Field exists but not serialized (may confuse developers)

#### Option C: Use Different DTO for HSM
```java
// Create HsmPinBlockVerificationRequest (without encryptionAlgorithm)
// Map from internal DTO to HSM DTO before sending
```

**Pros:**
- Clear separation between internal and external DTOs
- Can add HSM-specific validation

**Cons:**
- More classes to maintain

**Recommendation:** Use Option B (@JsonIgnore) to maintain internal tracking while ensuring HSM compatibility.

### Issue 2: Response Field Mismatch

**Severity:** ℹ️ **Low**

**Current Response DTO:** `PinBlockVerificationResponse.java`

Has extra field:
```java
private PinEncryptionAlgorithm encryptionAlgorithm; // Not in HSM response
```

**Recommendation:** Either remove or mark as `@JsonIgnore` for consistency.

## Integration Test Scenarios

### Scenario 1: Method A with 3DES (Field 52)

```yaml
Configuration:
  encryption-algorithm: TDES
  pin.format: ISO_0

Flow:
  1. Terminal sends PIN in field 52 (8 bytes, hex-encoded)
  2. ATM extracts from field 52
  3. ATM calls /api/hsm/pin/verify-with-translation
  4. HSM decrypts with TPK → compares with LMK-encrypted PIN
  5. Result: ✅ Should work (after removing encryptionAlgorithm field)
```

### Scenario 2: Method A with AES-128 (Field 123)

```yaml
Configuration:
  encryption-algorithm: AES_128
  pin.format: ISO_0

Flow:
  1. Terminal sends PIN in field 123 (16 bytes as 32 hex chars)
  2. ATM extracts from field 123
  3. ATM calls /api/hsm/pin/verify-with-translation
  4. HSM decrypts with TPK → compares with LMK-encrypted PIN
  5. Result: ✅ Should work (HSM supports AES)
```

### Scenario 3: Method B with PVV (Either Field)

```yaml
Configuration:
  encryption-algorithm: AES_128 or TDES
  pin.format: ISO_0

Flow:
  1. Terminal sends PIN in field 52 or 123
  2. ATM extracts from appropriate field
  3. ATM calls /api/hsm/pin/verify-with-pvv
  4. HSM decrypts PIN → generates PVV → compares with stored PVV
  5. Result: ✅ Works perfectly (no code changes needed)
```

## Required Changes

### 1. Fix PinBlockVerificationRequest DTO

**File:** `src/main/java/com/example/atm/dto/hsm/PinBlockVerificationRequest.java`

**Change:**
```java
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinBlockVerificationRequest {
    private String pinBlockUnderTPK;
    private String pinBlockUnderLMK;
    private String terminalId;
    private String pan;
    private PinFormat pinFormat;

    @JsonIgnore  // Don't send to HSM
    private PinEncryptionAlgorithm encryptionAlgorithm;
}
```

### 2. Fix PinBlockVerificationResponse DTO

**File:** `src/main/java/com/example/atm/dto/hsm/PinBlockVerificationResponse.java`

**Change:**
```java
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinBlockVerificationResponse {
    private boolean valid;
    private String message;
    private String terminalId;
    private String pan;
    private String pinFormat;
    private String lmkKeyId;
    private String tpkKeyId;

    @JsonIgnore  // Not in HSM response
    private PinEncryptionAlgorithm encryptionAlgorithm;
}
```

## Testing Checklist

After applying fixes:

- [ ] Test Method A with 3DES PIN block (field 52)
- [ ] Test Method A with AES-128 PIN block (field 123)
- [ ] Test Method B with 3DES PIN block (field 52)
- [ ] Test Method B with AES-128 PIN block (field 123)
- [ ] Verify HSM accepts requests without errors
- [ ] Verify responses are correctly parsed
- [ ] Test backward compatibility with field fallback
- [ ] Test MAC verification if enabled

## Conclusion

### Current Status

✅ **Method B (PVV)** - Ready to use, no changes needed
⚠️ **Method A (PIN Block)** - Requires minor DTO fix (`@JsonIgnore`)

### Compatibility Summary

| Component | HSM Compatible? | Notes |
|-----------|----------------|-------|
| Endpoints | ✅ Yes | Correctly configured |
| Request formats | ⚠️ Partial | Extra field in Method A |
| Response parsing | ✅ Yes | DTOs match expected responses |
| Encryption algorithms | ✅ Yes | AES-128 supported by both |
| PIN formats | ✅ Yes | ISO-0/1/3/4 supported |
| Field mapping | ✅ Yes | Both field 52 and 123 work |

### Recommendation

Apply the `@JsonIgnore` annotations to both request and response DTOs, then the application will be **fully compatible** with the artivisi HSM simulator for both PIN verification methods.

**Estimated effort:** 5 minutes (2 files, 2 annotations)
