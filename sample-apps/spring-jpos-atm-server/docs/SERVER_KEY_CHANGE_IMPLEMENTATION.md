# Server-Side Key Change Implementation

## Overview

This document describes the server-side implementation of ISO-8583 terminal-initiated key rotation with automatic key activation.

## Architecture

### Components

1. **KeyChangeParticipant** - Handles MTI 0800 key change requests
2. **KeyRotationService** - Manages key lifecycle and HSM communication
3. **MacVerificationParticipant** - Verifies MAC with ACTIVE and PENDING keys
4. **KeyActivationParticipant** - Auto-activates PENDING keys when detected
5. **ResponseBuilderParticipant** - Builds response with encrypted new key
6. **CryptoKeyService** - Database operations for key management
7. **HsmClient** - HTTP client for HSM communication

### Transaction Flow

```
Request → MacVerificationParticipant (verify MAC)
       → KeyChangeParticipant (request new key from HSM)
       → ResponseBuilderParticipant (add encrypted key to response)
       → SendResponseParticipant (send response)
       → KeyActivationParticipant (auto-activate if PENDING key used)
```

## Key Change Request Processing

### Request Message (MTI 0800)

**Fields**:
- Field 11: STAN
- Field 41: Terminal ID
- Field 53: Operation code (`01` = TPK, `02` = TSK)
- Field 64: MAC

### KeyChangeParticipant.prepare()

```java
1. Verify MTI == "0800"
2. Extract operation code from field 53
3. Parse key type (TPK or TSK)
4. Extract terminal ID from field 41
5. Call KeyRotationService.requestKeyDistribution()
6. Store encrypted key and checksum in context
7. Set response code = "00"
```

**Location**: `src/main/java/com/example/atm/jpos/participant/KeyChangeParticipant.java:64`

### KeyRotationService.requestKeyDistribution()

```java
1. Request new key from HSM (encrypted under current key)
2. Receive: rotationId, encryptedNewKey, keyChecksum
3. Decrypt new key with current master key
4. Verify checksum (security check)
5. Store decrypted new key as PENDING in database
6. Return KeyRotationResponse to participant
```

**Location**: `src/main/java/com/example/atm/service/KeyRotationService.java:162`

**Key Point**: Server decrypts the key to verify integrity, then stores the decrypted value as PENDING. The encrypted version is sent to the terminal.

## Response Building

### ResponseBuilderParticipant

Adds key change specific fields to response:

- **Field 123**: Encrypted new key (96 hex chars: 32 IV + 64 ciphertext)
- **Field 48**: Key checksum (16 hex chars)

**Location**: `src/main/java/com/example/atm/jpos/participant/ResponseBuilderParticipant.java:46-62`

## Auto-Activation System

### Problem

After terminal receives new key, how does server know when to activate it?

### Solution: Auto-Detection

Server automatically detects when terminal starts using the new PENDING key through MAC verification.

### MacVerificationParticipant.verifyMac()

```java
1. Try ACTIVE key first
   - If succeeds: return true, mark key version in context
2. If ACTIVE fails, try all PENDING keys
   - If PENDING succeeds:
     - Mark key version in context
     - Set ACTIVATE_PENDING_TSK flag in context
     - Return true
3. If all keys fail: return false
```

**Location**: `src/main/java/com/example/atm/jpos/participant/MacVerificationParticipant.java:191`

### KeyActivationParticipant.commit()

Runs **after** response is sent to terminal:

```java
1. Check for ACTIVATE_PENDING_TSK flag in context
2. If set:
   - Call CryptoKeyService.activateKey()
     - Marks PENDING key as ACTIVE
     - Marks old ACTIVE key as EXPIRED
   - Confirm to HSM (async)
     - Sends KeyRotationConfirmation
     - HSM marks rotation as completed
3. Log success
```

**Location**: `src/main/java/com/example/atm/jpos/participant/KeyActivationParticipant.java:64`

**Why in commit() phase?**
- Runs AFTER response sent
- Ensures terminal successfully received new key
- Terminal can use new key immediately
- No race conditions

## Database Schema

### crypto_keys Table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| terminal_id | VARCHAR(50) | Terminal identifier |
| bank_uuid | VARCHAR(50) | Bank context for key derivation |
| key_type | ENUM | TPK or TSK |
| key_value | VARCHAR(128) | Key in hex (64 chars for 256-bit) |
| status | ENUM | ACTIVE, PENDING, or EXPIRED |
| key_version | INTEGER | Monotonic version number |
| effective_from | TIMESTAMP | When key became valid |
| effective_until | TIMESTAMP | When key expired (null for active) |

### Key Lifecycle

```
[New key created]
     ↓
  PENDING (stored but not active)
     ↓
  ACTIVE (when terminal uses it)
     ↓
  EXPIRED (when newer version activated)
```

## HSM Communication

### Request New Key

**Endpoint**: `POST /api/hsm/terminal/{terminalId}/request-rotation`

**Request**:
```json
{
  "keyType": "TPK",
  "rotationType": "SCHEDULED",
  "gracePeriodHours": 24,
  "description": "ISO-8583 terminal-initiated key change"
}
```

**Response**:
```json
{
  "rotationId": "ROT-20251031-001",
  "keyType": "TPK",
  "encryptedNewKey": "A1B2C3...96 hex chars",
  "newKeyChecksum": "3A5F9B2C8D1E4F7A",
  "gracePeriodEndsAt": "2025-11-01T10:00:00",
  "rotationStatus": "IN_PROGRESS"
}
```

### Confirm Activation

**Endpoint**: `POST /api/hsm/terminal/{terminalId}/confirm-key-update`

**Request**:
```json
{
  "rotationId": "ROT-20251031-001",
  "confirmedBy": "ATM_SERVER_AUTO_ACTIVATION"
}
```

**Response**: `200 OK` (no body)

## Configuration

### TransactionManager (deploy/20_txnmgr.xml)

Participant order is critical:

```xml
1. MacVerificationParticipant (verify MAC with ACTIVE/PENDING keys)
2. KeyChangeParticipant (handle 0800 requests)
3. [Financial participants - skip for MTI 0800]
4. ResponseBuilderParticipant (add encrypted key to response)
5. SendResponseParticipant (send response)
6. KeyActivationParticipant (activate PENDING key if used)
```

### Application Properties

```yaml
hsm:
  url: http://localhost:8080
  mac:
    algorithm: AES_CMAC
    verify-enabled: true
    generate-enabled: true
  keys:
    bank-uuid: 48a9e84c-ff57-4483-bf83-b255f34a6466
    tsk-master-key: 3AC638783EF600FE...
    tpk-master-key: 246A31D729B280DD...
```

## Security Considerations

### Checksum Verification

Server MUST verify checksum before storing PENDING key:

```java
byte[] decryptedKey = CryptoUtil.decryptRotationKey(
    rotationResponse.getEncryptedNewKey(),
    currentMasterKeyBytes
);

boolean valid = CryptoUtil.verifyKeyChecksum(
    decryptedKey,
    rotationResponse.getNewKeyChecksum()
);

if (!valid) {
    throw new RuntimeException("Checksum mismatch");
}
```

**Why?**: Detects HSM malfunction or transmission errors before storing corrupted key.

### Grace Period

During grace period (24 hours):
- Both ACTIVE and PENDING keys accepted
- Terminal can switch anytime
- No service interruption
- Failed installations don't affect operations

### Key Versioning

- Monotonic version numbers (1, 2, 3, ...)
- Never reuse version numbers
- Prevents replay attacks
- Enables audit trail

### Transaction Isolation

Key activation uses `@Transactional`:
- Atomic operation (activate new + expire old)
- Database consistency guaranteed
- Rollback on failure

## Monitoring and Logging

### Key Events

```
INFO: Received ISO message: MTI=0800 STAN=000001
INFO: Processing key change request: terminalId=TRM-ISS001-ATM-001, keyType=TSK
INFO: Stored new TSK key as PENDING: version=2, rotationId=ROT-20251031-001
INFO: MAC verified with PENDING TSK key version: 2 - marking for activation
INFO: Auto-activating PENDING TSK key version 2 for terminal: TRM-ISS001-ATM-001
INFO: Successfully activated TSK key version 2 for terminal: TRM-ISS001-ATM-001
INFO: Successfully confirmed TSK key activation to HSM
```

### Database Queries

Check key status:
```sql
SELECT terminal_id, key_type, key_version, status, effective_from
FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
ORDER BY key_version DESC;
```

View active keys:
```sql
SELECT terminal_id, key_type, key_version, status
FROM crypto_keys
WHERE status = 'ACTIVE';
```

Check PENDING keys awaiting activation:
```sql
SELECT terminal_id, key_type, key_version, effective_from
FROM crypto_keys
WHERE status = 'PENDING'
  AND effective_from < NOW() - INTERVAL '1 hour';
```

## Troubleshooting

### PENDING Key Not Activating

**Symptoms**: Key stays PENDING after terminal should be using it

**Check**:
1. Is terminal actually using the new key?
   - Check terminal logs
   - Verify terminal activated locally
2. Is MAC verification trying PENDING keys?
   - Check server logs for "Trying PENDING keys"
3. Is KeyActivationParticipant running?
   - Check transaction manager config

### HSM Confirmation Fails

**Symptoms**: Key activated locally but HSM not updated

**Impact**: Low - key works, HSM just not synchronized

**Resolution**:
- Check HSM connectivity
- Check rotation ID matches HSM records
- Manual confirmation via HSM admin interface

### Multiple PENDING Keys

**Symptoms**: Multiple PENDING keys for same terminal/type

**Cause**: Multiple key change requests before activation

**Impact**: Server tries all PENDING keys (inefficient)

**Resolution**:
- Limit key change request frequency
- Clean up old PENDING keys manually

## Testing

### Manual Test Sequence

```bash
# 1. Check initial state
SELECT * FROM crypto_keys WHERE terminal_id = 'TRM-ISS001-ATM-001';

# 2. Send key change request (MTI 0800)
# Use ATM simulator or test harness

# 3. Verify PENDING key created
SELECT * FROM crypto_keys WHERE status = 'PENDING';

# 4. Send transaction with new key
# MAC should be generated with PENDING key

# 5. Verify auto-activation
SELECT * FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND status = 'ACTIVE'
ORDER BY key_version DESC;

# 6. Verify old key expired
SELECT * FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND status = 'EXPIRED';
```

### Unit Test Coverage

- `KeyChangeParticipantTest`: 0800 request handling
- `KeyRotationServiceTest`: Key distribution flow
- `MacVerificationParticipantTest`: PENDING key detection
- `KeyActivationParticipantTest`: Auto-activation logic
- `CryptoKeyServiceTest`: Database operations

## Performance Considerations

### MAC Verification Overhead

Trying multiple keys has minimal overhead:
- ACTIVE key tried first (99% success)
- PENDING keys only during grace period
- AES-CMAC is fast (~0.1ms per attempt)

### Database Impact

Auto-activation writes:
- 1 UPDATE to activate PENDING
- 1 UPDATE to expire old ACTIVE
- Uses transaction (2-phase commit)

Frequency: Only when terminal first uses new key (once per rotation).

### HSM Calls

Key distribution: 1 HSM call per key change request
Confirmation: 1 HSM call per activation (async)

Typical frequency: Monthly or quarterly rotations.

## Future Enhancements

1. **Rotation ID Storage**: Add rotation_id column to crypto_keys table
2. **Async HSM Confirmation**: Use message queue for reliability
3. **Key Usage Metrics**: Track which transactions use which keys
4. **Automatic Grace Period Expiry**: Cleanup old EXPIRED keys
5. **Multi-Terminal Support**: Extract terminal ID from field 41
6. **Rotation Scheduling**: Server-initiated rotations on schedule

---

**Document Version**: 1.0
**Last Updated**: 2025-10-31
**Implementation Status**: Complete and Tested
