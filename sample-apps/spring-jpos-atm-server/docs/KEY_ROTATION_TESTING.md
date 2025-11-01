# Key Rotation Testing Guide

## Table of Contents
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Pre-Test Verification](#pre-test-verification)
- [Terminal-Initiated Testing](#terminal-initiated-testing)
- [Server-Initiated Testing](#server-initiated-testing)
- [Error Scenario Testing](#error-scenario-testing)
- [End-to-End Verification](#end-to-end-verification)
- [Performance Testing](#performance-testing)
- [Security Testing](#security-testing)
- [Automated Testing](#automated-testing)

## Overview

This guide provides comprehensive testing procedures for both terminal-initiated and server-initiated key rotation. Testing key rotation is critical because:

- Cryptographic key management is security-critical
- Failures can break transaction processing
- Both server and terminal must stay synchronized
- HSM integration adds complexity
- Multiple failure modes must be tested

**Test Coverage:**
- ✅ Happy path (successful rotation)
- ✅ Error handling (checksum mismatch, decryption failure)
- ✅ Network issues (timeout, disconnection)
- ✅ Confirmation mechanism
- ✅ Key synchronization (terminal, server, HSM)
- ✅ Transaction processing with new keys
- ✅ Both terminal-initiated and server-initiated flows

## Prerequisites

### System Components

All components must be running and healthy:

| Component | Default Port | Health Check | Purpose |
|-----------|-------------|--------------|---------|
| **ATM Simulator** | 7070 (HTTP), connects to 22222 | `curl http://localhost:7070/actuator/health` | Terminal simulation |
| **ATM Server** | 22222 (ISO-8583) | Check logs for "Server started" | Process transactions, key rotation |
| **HSM Simulator** | 8080 (HTTP) | `curl http://localhost:8080/actuator/health` | Key generation, encryption |
| **PostgreSQL** | 5432 | `psql -U postgres -c "SELECT 1"` | Key storage, audit logs |

### Initial State

Before testing, verify:

```bash
# 1. Check all services are running
ps aux | grep -E "java.*atm-(simulator|server)|postgres|hsm"

# 2. Verify database connectivity
psql -U postgres -d atm_bank -c "SELECT count(*) FROM crypto_keys;"

# 3. Check terminal connection
curl http://localhost:7070/api/debug/connection-status

# 4. Verify HSM is accessible
curl http://localhost:8080/actuator/health
```

### Test Tools

Install required tools:

```bash
# curl for API testing
which curl || sudo apt-get install curl

# jq for JSON parsing
which jq || sudo apt-get install jq

# PostgreSQL client
which psql || sudo apt-get install postgresql-client
```

## Environment Setup

### Test Configuration

File: `application-test.yml`

```yaml
# Terminal configuration
terminal:
  id: "ATM-001"
  institution:
    id: "TRM-ISS001"

# Server configuration
server:
  port: 22222

# HSM configuration
hsm:
  url: http://localhost:8080
  mac:
    algorithm: AES_CMAC
    verify-enabled: true
  keys:
    bank-uuid: 48a9e84c-ff57-4483-bf83-b255f34a6466

# Logging for tests
logging:
  level:
    com.example.atm: DEBUG
    org.jpos: INFO
```

### Database Setup

Create test database with known initial state:

```sql
-- Reset crypto_keys table
TRUNCATE TABLE crypto_keys CASCADE;

-- Insert initial keys for testing
INSERT INTO crypto_keys (id, terminal_id, bank_uuid, key_type, key_value, status, key_version, effective_from)
VALUES
  (gen_random_uuid(), 'TRM-ISS001-ATM-001', '48a9e84c-ff57-4483-bf83-b255f34a6466',
   'TPK', 'AAAA...', 'ACTIVE', 1, NOW()),
  (gen_random_uuid(), 'TRM-ISS001-ATM-001', '48a9e84c-ff57-4483-bf83-b255f34a6466',
   'TSK', 'BBBB...', 'ACTIVE', 1, NOW());

-- Verify initial state
SELECT terminal_id, key_type, key_version, status FROM crypto_keys;
```

### Test Data

**Test PAN:** `4111111111111111`
**Test PIN:** `1234`
**Test Terminal ID:** `TRM-ISS001-ATM-001`

## Pre-Test Verification

### Step 1: Verify System Health

**Terminal Health:**
```bash
curl http://localhost:7070/actuator/health
```

Expected:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

**Server Health:**
```bash
# Check logs
tail -20 logs/server.log | grep -i "started\|ready"
```

Expected: No errors, "Server started successfully"

**HSM Health:**
```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

### Step 2: Verify Terminal Connection

```bash
curl http://localhost:7070/api/debug/connection-status
```

Expected:
```json
{
  "connected": true,
  "signedOn": true,
  "terminalId": "TRM-ISS001-ATM-001"
}
```

**If not signed on:**
```bash
curl -X POST http://localhost:7070/api/auth/sign-on
```

### Step 3: Verify Current Keys Work

Test balance inquiry (uses both TPK for PIN and TSK for MAC):

```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{
    "pan": "4111111111111111",
    "pin": "1234"
  }'
```

Expected:
```json
{
  "success": true,
  "responseCode": "00",
  "balance": 1000.00,
  "message": "Balance inquiry successful"
}
```

**Verify in logs:**
- Terminal: "MAC verification successful"
- Server: "MAC verified with ACTIVE TSK key"
- HSM: "PIN verification successful"

### Step 4: Check Initial Key Versions

```bash
psql -U postgres -d atm_bank -c "
SELECT terminal_id, key_type, key_version, status, check_value
FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND status = 'ACTIVE'
ORDER BY key_type;
"
```

Expected:
```
     terminal_id      | key_type | key_version | status | check_value
----------------------+----------+-------------+--------+-------------
 TRM-ISS001-ATM-001  | TPK      |           1 | ACTIVE | 1A2B3C4D5E6F
 TRM-ISS001-ATM-001  | TSK      |           1 | ACTIVE | 9F8E7D6C5B4A
```

## Terminal-Initiated Testing

### Test 1: Successful TPK Rotation

**Objective:** Verify complete TPK rotation flow including confirmation

**Execute:**
```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' \
  | jq '.'
```

**Expected Response:**
```json
{
  "success": true,
  "keyType": "TPK",
  "keyId": "uuid-here",
  "checkValue": "3A5F9B2C8D1E4F7A",
  "message": "Key changed successfully via ISO-8583"
}
```

**Terminal Log Verification:**
```bash
tail -50 logs/atm.log | grep -i "key change"
```

Expected sequence:
```
INFO: Initiating key change for key type: TPK
INFO: Sending key change request via MUX
INFO: Key change approved by server, processing encrypted key
INFO: Decrypted new key: 32 bytes
INFO: Checksum verification successful: 3A5F9B2C8D1E4F7A
INFO: Key change completed successfully for TPK: keyId=..., checksum=3A5F9B2C8D1E4F7A
INFO: Key installation successful, sending confirmation to server
INFO: Server acknowledged key installation confirmation: response code 00
```

**Server Log Verification:**
```bash
tail -50 logs/server.log | grep -i "key.*TPK"
```

Expected sequence:
```
INFO: Processing key change request: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Generated new TPK key: version=2, checksum=3A5F9B2C8D1E4F7A
INFO: Stored new TPK key as PENDING: version=2
INFO: Sent encrypted key to terminal: field123=..., field48=3A5F9B2C8D1E4F7A
INFO: Processing key confirmation: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: MAC verified with PENDING TPK key version: 2
INFO: Explicit confirmation received for TPK key installation
INFO: Successfully activated TPK key version 2 for terminal: TRM-ISS001-ATM-001
INFO: Successfully confirmed TPK key activation to HSM
```

**Database Verification:**
```sql
-- Check new TPK is ACTIVE, old is EXPIRED
SELECT terminal_id, key_type, key_version, status, check_value, effective_from
FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND key_type = 'TPK'
ORDER BY key_version DESC
LIMIT 2;
```

Expected:
```
     terminal_id      | key_type | key_version | status  | check_value      | effective_from
----------------------+----------+-------------+---------+------------------+---------------------
 TRM-ISS001-ATM-001  | TPK      |           2 | ACTIVE  | 3A5F9B2C8D1E4F7A | 2025-11-01 10:15:30
 TRM-ISS001-ATM-001  | TPK      |           1 | EXPIRED | 1A2B3C4D5E6F     | 2025-10-01 09:00:00
```

**HSM Verification:**
```bash
curl "http://localhost:8080/api/hsm/terminal/TRM-ISS001-ATM-001/keys?keyType=TPK" | jq '.'
```

Expected: Version 2 marked as ACTIVE

### Test 2: Successful TSK Rotation

**Execute:**
```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TSK"}' \
  | jq '.'
```

**Expected:** Similar to Test 1, but for TSK

**Critical Verification:** After TSK rotation, the confirmation message itself uses the NEW TSK for MAC generation, proving the terminal has the working key.

**Server Log:**
```
INFO: MAC verified with PENDING TSK key version: 2  ← Proves terminal has new key
```

### Test 3: Post-Rotation Transaction Processing

**Objective:** Verify new keys work for actual transactions

**Execute PIN transaction (uses new TPK):**
```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{"pan": "4111111111111111", "pin": "1234"}' \
  | jq '.'
```

**Expected:**
```json
{
  "success": true,
  "responseCode": "00",
  "balance": 1000.00
}
```

**Terminal Log Verification:**
```bash
grep "Using.*key version" logs/atm.log | tail -5
```

Expected:
```
INFO: Using TPK key version 2 for PIN encryption
INFO: Using TSK key version 2 for MAC generation
```

**Server Log Verification:**
```bash
grep "MAC verified with.*key version" logs/server.log | tail -5
```

Expected:
```
INFO: MAC verified with ACTIVE TSK key version: 2
```

**HSM Log Verification:**
```bash
grep "PIN.*version 2" logs/hsm.log | tail -3
```

Expected:
```
INFO: Decrypting PIN with TPK version 2
INFO: PIN verification successful
```

### Test 4: Multiple Sequential Rotations

**Objective:** Verify version incrementing and old key cleanup

**Execute:**
```bash
# Rotate TPK three times
for i in {1..3}; do
    echo "Rotation $i:"
    curl -X POST http://localhost:7070/api/keys/change \
      -H "Content-Type: application/json" \
      -d '{"keyType": "TPK"}' \
      | jq '.keyId, .checkValue'
    sleep 5
done
```

**Database Verification:**
```sql
SELECT key_type, key_version, status, effective_from
FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND key_type = 'TPK'
ORDER BY key_version DESC;
```

Expected:
```
 key_type | key_version | status  | effective_from
----------+-------------+---------+---------------------
 TPK      |           5 | ACTIVE  | 2025-11-01 10:20:45
 TPK      |           4 | EXPIRED | 2025-11-01 10:20:40
 TPK      |           3 | EXPIRED | 2025-11-01 10:20:35
 TPK      |           2 | EXPIRED | 2025-11-01 10:15:30
 TPK      |           1 | EXPIRED | 2025-10-01 09:00:00
```

**Verify monotonic versioning:** Each rotation increments version by 1.

## Server-Initiated Testing

### Test 5: List Connected Terminals

**Execute:**
```bash
curl http://localhost:22222/api/admin/keys/connected-terminals | jq '.'
```

**Expected:**
```json
{
  "success": true,
  "count": 1,
  "terminals": ["TRM-ISS001-ATM-001"]
}
```

### Test 6: Check Terminal Connection Status

**Execute:**
```bash
curl http://localhost:22222/api/admin/keys/status/TRM-ISS001-ATM-001 | jq '.'
```

**Expected:**
```json
{
  "success": true,
  "terminalId": "TRM-ISS001-ATM-001",
  "connected": true
}
```

### Test 7: Server-Initiated TPK Rotation

**Objective:** Verify complete server-initiated flow

**Execute:**
```bash
curl -X POST "http://localhost:22222/api/admin/keys/rotate/TRM-ISS001-ATM-001?keyType=TPK" | jq '.'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Key rotation notification sent to terminal",
  "terminalId": "TRM-ISS001-ATM-001",
  "keyType": "TPK",
  "nextStep": "Terminal will initiate standard key change flow (operation 01/02)"
}
```

**Server Log Verification:**
```bash
tail -100 logs/server.log | grep -E "Admin key rotation|notification|key change request"
```

Expected sequence:
```
INFO: Admin key rotation request: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Initiating server-side key rotation
INFO: Sending key rotation notification to terminal
INFO: Key rotation notification sent successfully
[~5 seconds later]
INFO: Processing key change request: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Stored new TPK key as PENDING: version=3
[After confirmation]
INFO: Successfully activated TPK key version 3
```

**Terminal Log Verification:**
```bash
tail -100 logs/atm.log | grep -E "notification|auto-key-change|Initiating key change"
```

Expected sequence:
```
INFO: Processing unhandled message: MTI=0800
INFO: Detected operation code: 07
INFO: Handling server-initiated key rotation notification
INFO: Received key rotation notification from server
INFO: Server requesting key rotation: keyType=TPK
INFO: Sent acknowledgment to server, initiating key change
INFO: Initiating automatic key change: keyType=TPK
INFO: Initiating key change for key type: TPK
[Standard terminal-initiated flow continues...]
```

### Test 8: Server-Initiated TSK Rotation

Same as Test 7, but with `keyType=TSK`:

```bash
curl -X POST "http://localhost:22222/api/admin/keys/rotate/TRM-ISS001-ATM-001?keyType=TSK" | jq '.'
```

### Test 9: Mass Rotation (Multiple Terminals)

**Setup:** Start multiple terminal simulators (or simulate)

**Execute:**
```bash
#!/bin/bash
TERMINALS=$(curl -s http://localhost:22222/api/admin/keys/connected-terminals | jq -r '.terminals[]')

for TERMINAL in $TERMINALS; do
    echo "Rotating TPK for: $TERMINAL"
    curl -X POST "http://localhost:22222/api/admin/keys/rotate/$TERMINAL?keyType=TPK" | jq '.success'
    sleep 5  # Delay between rotations
done
```

**Verify:** All terminals successfully rotated keys.

## Error Scenario Testing

### Test 10: Checksum Mismatch Simulation

**Objective:** Verify terminal rejects keys with invalid checksums

**Setup:** Temporarily modify HSM to return incorrect checksum

**In HSM code:**
```java
// In KeyGenerationController.generateKey()
String badChecksum = "FFFFFFFFFFFFFFFF";  // Wrong checksum
response.setNewKeyChecksum(badChecksum);
```

**Execute:**
```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' \
  | jq '.'
```

**Expected Response:**
```json
{
  "success": false,
  "error": "Key change failed: SHA-256 checksum verification failed"
}
```

**Terminal Log:**
```
ERROR: Checksum mismatch! Expected: FFFFFFFFFFFFFFFF, Calculated: 3A5F9B2C8D1E4F7A
ERROR: Key change failed for TPK: SHA-256 checksum verification failed
INFO: Sending key installation FAILURE notification for TPK: operation code 05
```

**Server Log:**
```
INFO: Processing key installation failure: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Removing PENDING TPK key due to terminal installation failure
INFO: Notified HSM of key installation failure
```

**Database Verification:**
```sql
-- No PENDING key should exist
SELECT * FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND key_type = 'TPK'
  AND status = 'PENDING';
```

Expected: 0 rows (PENDING key was removed)

**Old key still ACTIVE:**
```sql
SELECT key_version, status FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND key_type = 'TPK'
  AND status = 'ACTIVE';
```

Expected: Old key (version N) still ACTIVE

**Restore HSM:** Remove the checksum modification

### Test 11: Network Timeout During Key Request

**Objective:** Verify timeout handling

**Setup:** Stop HSM temporarily or add artificial delay

```bash
# Stop HSM
systemctl stop hsm-simulator

# Or add delay in HSM code
Thread.sleep(35000);  // Exceeds 30s timeout
```

**Execute:**
```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' \
  | jq '.'
```

**Expected Response (after ~30 seconds):**
```json
{
  "success": false,
  "error": "Key change failed: No response from server for key change request"
}
```

**Terminal Log:**
```
INFO: Sending key change request via MUX
ERROR: Key change failed: No response from server for key change request
```

**Verify old key still works:**
```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{"pan": "4111111111111111", "pin": "1234"}' \
  | jq '.responseCode'
```

Expected: `"00"` (old key still active)

**Restore HSM:**
```bash
systemctl start hsm-simulator
```

### Test 12: Terminal Disconnection During Rotation

**Objective:** Verify server handles terminal disconnect

**Setup:**
```bash
# Start key rotation
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' &

# Immediately kill terminal (within 2 seconds)
sleep 2
pkill -f "atm-simulator"
```

**Server Log:**
```
INFO: Processing key change request: terminalId=TRM-ISS001-ATM-001, keyType=TPK
INFO: Stored new TPK key as PENDING
ERROR: Channel disconnected: terminalId=TRM-ISS001-ATM-001
```

**Database Verification:**
```sql
-- PENDING key exists but not activated
SELECT key_version, status FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND key_type = 'TPK'
ORDER BY key_version DESC
LIMIT 2;
```

Expected:
```
 key_version | status
-------------+---------
           2 | PENDING
           1 | ACTIVE
```

**Restart Terminal:**
```bash
./start-atm-simulator.sh
```

**Cleanup PENDING key (if needed):**
```sql
DELETE FROM crypto_keys
WHERE status = 'PENDING'
  AND effective_from < NOW() - INTERVAL '1 hour';
```

### Test 13: Server Error Response (Code 96)

**Objective:** Verify terminal handles server errors

**Setup:** Temporarily stop HSM

```bash
systemctl stop hsm-simulator
```

**Execute:**
```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' \
  | jq '.'
```

**Expected Response:**
```json
{
  "success": false,
  "error": "Key change failed with response code: 96"
}
```

**Server Log:**
```
ERROR: Failed to communicate with HSM: Connection refused
INFO: Returning error response code 96 to terminal
```

**Terminal Log:**
```
ERROR: Key change failed with response code: 96
INFO: Will retry with exponential backoff
```

**Restore HSM:**
```bash
systemctl start hsm-simulator
```

### Test 14: Server-Initiated with Terminal Offline

**Execute:**
```bash
# Stop terminal
systemctl stop atm-simulator

# Attempt server-initiated rotation
curl -X POST "http://localhost:22222/api/admin/keys/rotate/TRM-ISS001-ATM-001?keyType=TPK" | jq '.'
```

**Expected Response (400 Bad Request):**
```json
{
  "success": false,
  "error": "Terminal not connected",
  "terminalId": "TRM-ISS001-ATM-001"
}
```

**Server Log:**
```
ERROR: Cannot initiate key rotation: terminal not connected: TRM-ISS001-ATM-001
```

**Restore:**
```bash
systemctl start atm-simulator
```

## End-to-End Verification

### Test 15: Complete Rotation Cycle with Transaction Verification

**Objective:** Full workflow validation

**Step 1: Verify initial state**
```bash
# Check current key versions
psql -U postgres -d atm_bank -c "SELECT key_type, key_version, status FROM crypto_keys WHERE terminal_id = 'TRM-ISS001-ATM-001' AND status = 'ACTIVE';"
```

**Step 2: Perform transaction with old keys**
```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{"pan": "4111111111111111", "pin": "1234"}' \
  | jq '.responseCode'
```

Expected: `"00"`

**Step 3: Rotate both keys**
```bash
# Rotate TPK
curl -X POST http://localhost:7070/api/keys/change \
  -d '{"keyType": "TPK"}' \
  -H "Content-Type: application/json" | jq '.success'

sleep 5

# Rotate TSK
curl -X POST http://localhost:7070/api/keys/change \
  -d '{"keyType": "TSK"}' \
  -H "Content-Type: application/json" | jq '.success'
```

Expected: Both return `true`

**Step 4: Verify new key versions**
```bash
psql -U postgres -d atm_bank -c "SELECT key_type, key_version, status FROM crypto_keys WHERE terminal_id = 'TRM-ISS001-ATM-001' AND status = 'ACTIVE';"
```

Expected: Versions incremented (e.g., TPK=N+1, TSK=M+1)

**Step 5: Perform transaction with new keys**
```bash
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{"pan": "4111111111111111", "pin": "1234"}' \
  | jq '.'
```

**Expected:**
```json
{
  "success": true,
  "responseCode": "00",
  "balance": 1000.00
}
```

**Step 6: Verify in logs (all components using new keys)**
```bash
# Terminal
grep "Using.*key version" logs/atm.log | tail -5

# Server
grep "MAC verified.*key version" logs/server.log | tail -5

# HSM
grep "Decrypt.*key version" logs/hsm.log | tail -5
```

**Step 7: Verify HSM synchronization**
```bash
curl "http://localhost:8080/api/hsm/terminal/TRM-ISS001-ATM-001/keys" | jq '.[] | {keyType, version, status}'
```

Expected: Both TPK and TSK show new versions as ACTIVE

## Performance Testing

### Test 16: Key Rotation Duration

**Objective:** Measure time to complete rotation

**Execute:**
```bash
time curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' \
  -o /dev/null -s -w "%{time_total}\n"
```

**Expected Duration:**
- Terminal-initiated: 2-5 seconds
- Server-initiated (including notification): 3-7 seconds

**Breakdown:**
1. Key request: ~500ms
2. HSM key generation: ~1000ms
3. Decrypt/verify: ~200ms
4. Confirmation: ~500ms
5. Activation: ~300ms

**Bottlenecks:**
- HSM key generation (crypto operations)
- Database writes (PENDING → ACTIVE)
- Network latency

### Test 17: Concurrent Rotations (Load Test)

**Objective:** Verify system handles multiple simultaneous rotations

**Setup:** Multiple terminal simulators or parallel requests

**Execute:**
```bash
#!/bin/bash
for i in {1..10}; do
    (
        curl -X POST http://localhost:7070/api/keys/change \
          -H "Content-Type: application/json" \
          -d "{\"keyType\": \"TPK\"}" \
          -o /dev/null -s -w "Request $i: %{http_code} in %{time_total}s\n"
    ) &
done
wait
```

**Expected:**
- All requests complete successfully
- No database deadlocks
- No key version conflicts
- Response times remain reasonable (<10s)

**Monitor:**
```bash
# Database connections
psql -U postgres -c "SELECT count(*) FROM pg_stat_activity WHERE datname = 'atm_bank';"

# Server threads
jstack <server-pid> | grep -i "key.*rotation"
```

### Test 18: Transaction Throughput During Rotation

**Objective:** Verify transactions continue during key rotation

**Execute in terminal 1:**
```bash
curl -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}'
```

**Execute in terminal 2 (immediately):**
```bash
for i in {1..20}; do
    curl -X POST http://localhost:7070/api/transactions/balance \
      -H "Content-Type: application/json" \
      -d '{"pan": "4111111111111111", "pin": "1234"}' \
      -s -w "Transaction $i: %{http_code}\n" \
      -o /dev/null
    sleep 0.5
done
```

**Expected:**
- All 20 transactions succeed (200 OK, response code "00")
- No transaction failures during rotation
- Grace period allows both old and new keys

## Security Testing

### Test 19: Verify Checksum Algorithm

**Objective:** Ensure server and terminal use same SHA-256 algorithm

**Execute:**
```bash
# Trigger rotation and capture checksum
CHECKSUM=$(curl -s -X POST http://localhost:7070/api/keys/change \
  -H "Content-Type: application/json" \
  -d '{"keyType": "TPK"}' | jq -r '.checkValue')

echo "Checksum from response: $CHECKSUM"

# Verify in database
psql -U postgres -d atm_bank -c "SELECT check_value FROM crypto_keys WHERE check_value = '$CHECKSUM';"
```

**Expected:** Checksum matches in database (proves server calculated same value)

### Test 20: Key Version Rollback Prevention

**Objective:** Verify server rejects old key versions

**Setup:** Attempt to manually insert old key version

```sql
-- Try to insert lower version
INSERT INTO crypto_keys (id, terminal_id, key_type, key_value, status, key_version)
VALUES (gen_random_uuid(), 'TRM-ISS001-ATM-001', 'TPK', 'OLDKEY...', 'ACTIVE', 1);
```

**Expected:** Unique constraint violation or application validation error

**Application Test:**
```java
// In KeyRotationService
if (newKeyVersion <= currentKeyVersion) {
    throw new SecurityException("Key version rollback detected");
}
```

### Test 21: Grace Period Functionality

**Objective:** Verify both old and new keys accepted during grace period

**Execute:**
```bash
# Rotate TPK
curl -X POST http://localhost:7070/api/keys/change \
  -d '{"keyType": "TPK"}' \
  -H "Content-Type: application/json"

# Immediately test transaction (should work with new key)
curl -X POST http://localhost:7070/api/transactions/balance \
  -H "Content-Type: application/json" \
  -d '{"pan": "4111111111111111", "pin": "1234"}' \
  | jq '.responseCode'
```

Expected: `"00"`

**Check database:**
```sql
SELECT key_type, key_version, status, effective_until FROM crypto_keys
WHERE terminal_id = 'TRM-ISS001-ATM-001'
  AND key_type = 'TPK'
ORDER BY key_version DESC
LIMIT 2;
```

Expected:
```
 key_type | key_version | status  | effective_until
----------+-------------+---------+---------------------
 TPK      |           2 | ACTIVE  | NULL
 TPK      |           1 | EXPIRED | 2025-11-02 10:15:30  ← 24 hours grace period
```

**Verify grace period:**
```sql
SELECT NOW() < effective_until AS within_grace_period
FROM crypto_keys
WHERE key_version = 1 AND key_type = 'TPK';
```

Expected: `true` (within grace period)

## Automated Testing

### Test Suite Script

File: `test-key-rotation.sh`

```bash
#!/bin/bash

# Configuration
SERVER_URL="http://localhost:22222"
TERMINAL_URL="http://localhost:7070"
TERMINAL_ID="TRM-ISS001-ATM-001"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Test counter
TESTS_RUN=0
TESTS_PASSED=0

# Test function
test_case() {
    TESTS_RUN=$((TESTS_RUN + 1))
    echo -e "\n=== Test $TESTS_RUN: $1 ==="
}

pass() {
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}✓ PASS${NC}: $1"
}

fail() {
    echo -e "${RED}✗ FAIL${NC}: $1"
}

# Test 1: Terminal Connection
test_case "Verify Terminal Connected"
CONNECTED=$(curl -s "$TERMINAL_URL/api/debug/connection-status" | jq -r '.connected')
if [ "$CONNECTED" = "true" ]; then
    pass "Terminal connected"
else
    fail "Terminal not connected"
    exit 1
fi

# Test 2: Pre-rotation transaction
test_case "Pre-rotation Transaction"
RESPONSE=$(curl -s -X POST "$TERMINAL_URL/api/transactions/balance" \
    -H "Content-Type: application/json" \
    -d '{"pan": "4111111111111111", "pin": "1234"}' \
    | jq -r '.responseCode')
if [ "$RESPONSE" = "00" ]; then
    pass "Transaction successful with current keys"
else
    fail "Transaction failed: $RESPONSE"
fi

# Test 3: Terminal-Initiated TPK Rotation
test_case "Terminal-Initiated TPK Rotation"
RESULT=$(curl -s -X POST "$TERMINAL_URL/api/keys/change" \
    -H "Content-Type: application/json" \
    -d '{"keyType": "TPK"}' \
    | jq -r '.success')
if [ "$RESULT" = "true" ]; then
    pass "TPK rotation successful"
else
    fail "TPK rotation failed"
fi

sleep 2

# Test 4: Post-rotation transaction
test_case "Post-rotation Transaction"
RESPONSE=$(curl -s -X POST "$TERMINAL_URL/api/transactions/balance" \
    -H "Content-Type: application/json" \
    -d '{"pan": "4111111111111111", "pin": "1234"}' \
    | jq -r '.responseCode')
if [ "$RESPONSE" = "00" ]; then
    pass "Transaction successful with new TPK"
else
    fail "Transaction failed with new TPK: $RESPONSE"
fi

# Test 5: Terminal-Initiated TSK Rotation
test_case "Terminal-Initiated TSK Rotation"
RESULT=$(curl -s -X POST "$TERMINAL_URL/api/keys/change" \
    -H "Content-Type: application/json" \
    -d '{"keyType": "TSK"}' \
    | jq -r '.success')
if [ "$RESULT" = "true" ]; then
    pass "TSK rotation successful"
else
    fail "TSK rotation failed"
fi

sleep 2

# Test 6: Transaction with both new keys
test_case "Transaction with New TPK and TSK"
RESPONSE=$(curl -s -X POST "$TERMINAL_URL/api/transactions/balance" \
    -H "Content-Type: application/json" \
    -d '{"pan": "4111111111111111", "pin": "1234"}' \
    | jq -r '.responseCode')
if [ "$RESPONSE" = "00" ]; then
    pass "Transaction successful with both new keys"
else
    fail "Transaction failed: $RESPONSE"
fi

# Test 7: Server-Initiated TPK Rotation
test_case "Server-Initiated TPK Rotation"
RESULT=$(curl -s -X POST "$SERVER_URL/api/admin/keys/rotate/$TERMINAL_ID?keyType=TPK" \
    | jq -r '.success')
if [ "$RESULT" = "true" ]; then
    pass "Server-initiated notification sent"
else
    fail "Server-initiated notification failed"
fi

sleep 10  # Wait for auto-initiation

# Test 8: Verify server-initiated rotation completed
test_case "Verify Server-Initiated Rotation Completed"
RESPONSE=$(curl -s -X POST "$TERMINAL_URL/api/transactions/balance" \
    -H "Content-Type: application/json" \
    -d '{"pan": "4111111111111111", "pin": "1234"}' \
    | jq -r '.responseCode')
if [ "$RESPONSE" = "00" ]; then
    pass "Server-initiated rotation completed successfully"
else
    fail "Server-initiated rotation incomplete: $RESPONSE"
fi

# Summary
echo -e "\n========================================="
echo -e "Tests Run: $TESTS_RUN"
echo -e "Tests Passed: $TESTS_PASSED"
echo -e "Tests Failed: $((TESTS_RUN - TESTS_PASSED))"
if [ $TESTS_RUN -eq $TESTS_PASSED ]; then
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
else
    echo -e "${RED}SOME TESTS FAILED${NC}"
    exit 1
fi
```

**Execute:**
```bash
chmod +x test-key-rotation.sh
./test-key-rotation.sh
```

### Continuous Integration

File: `.github/workflows/key-rotation-test.yml`

```yaml
name: Key Rotation Tests

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: atm_bank
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Start HSM Simulator
        run: |
          cd hsm-simulator
          mvn spring-boot:run &
          sleep 30

      - name: Start ATM Server
        run: |
          cd spring-jpos-atm-server
          mvn spring-boot:run &
          sleep 30

      - name: Start ATM Simulator
        run: |
          cd spring-jpos-atm-simulator
          mvn spring-boot:run &
          sleep 30

      - name: Run Key Rotation Tests
        run: ./test-key-rotation.sh

      - name: Check Logs
        if: failure()
        run: |
          cat spring-jpos-atm-server/logs/server.log
          cat spring-jpos-atm-simulator/logs/atm.log
```

## Related Documentation

- **[Key Rotation Overview](KEY_ROTATION_OVERVIEW.md)** - High-level overview and flow diagrams
- **[Terminal-Initiated Key Rotation](KEY_ROTATION_TERMINAL_INITIATED.md)** - Detailed protocol specification
- **[Server-Initiated Key Rotation](KEY_ROTATION_SERVER_INITIATED.md)** - Remote rotation triggering
- **[Quick Reference](KEY_CHANGE_QUICK_REFERENCE.md)** - Field formats and codes

---

**Document Version:** 1.0
**Last Updated:** 2025-11-01
**Status:** Production Ready
