# Integration Test Guide

## Overview

This project includes **real integration tests** that communicate with the actual HSM simulator. No mocking is used - these tests verify end-to-end functionality.

## Test Configuration

- **Test File:** `JposServerIntegrationTest.java`
- **HSM:** Real HSM simulator on http://localhost:8080
- **Purpose:** End-to-end validation with actual HSM
- **Database:** PostgreSQL (same as production)
- **Run:** `mvn test` (no environment variables needed)

## Prerequisites for Integration Tests

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

The integration tests use the same PostgreSQL database as the main application.

### 2. Start HSM Simulator

**Option A: Using Docker (Recommended)**
```bash
# Clone HSM simulator
git clone https://github.com/artivisi/hsm-simulator.git
cd hsm-simulator

# Build and run
./mvnw spring-boot:run

# Or with Docker
docker build -t hsm-simulator .
docker run -p 8080:8080 hsm-simulator
```

**Option B: Using JAR**
```bash
java -jar hsm-simulator.jar
```

**Verify HSM is running:**
```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

### 3. HSM Test Configuration

The integration tests use HSM simulator's **pre-seeded sample data** - no manual setup required!

**Pre-configured in HSM:**
- Terminal: `TRM-ISS001-ATM-001`
- TPK Key: `TPK-TRM-ISS001-ATM-001` (AES-256: `246A31D7...3748B0`)
- Test PAN: `4111111111111111`
- Test PIN: `1234`

**How it works:**
1. Tests send field 41 = `ATM-001` and field 42 = `TRM-ISS001`
2. Application combines them → `TRM-ISS001-ATM-001` for HSM calls
3. HSM recognizes the pre-configured terminal
4. PIN verification uses AES-256 TPK from HSM sample data

**ISO-8583 Field Mapping:**
- Field 41 (16 bytes): Terminal ID = `ATM-001`
- Field 42 (15 bytes): Card Acceptor ID (institution) = `TRM-ISS001`
- Combined for HSM: `TRM-ISS001-ATM-001`

**No HSM setup required** - just start the HSM simulator and run tests!

### 4. Configure Test Account in Application Database

The tests use account `1234567890` with PIN `1234`. The H2 in-memory database is automatically seeded via Flyway migrations.

**Required data (already in migrations):**
- Account: `1234567890`
- Balance: `5000000.00`
- PIN data: encrypted PIN block or PVV for PIN `1234`

## Running Integration Tests

### Run All Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=JposServerIntegrationTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=JposServerIntegrationTest#testBalanceInquiryWithPinIntegration
```

## Test Scenarios

### 1. Balance Inquiry with PIN

**Test:** `testBalanceInquiryWithPinIntegration()`

**Flow:**
1. Generate 3DES PIN block using test TPK
2. Send ISO-8583 balance inquiry (MTI 0200, processing code 310000)
3. jPOS server receives request
4. MacVerificationParticipant (skipped if MAC disabled in test config)
5. PinVerificationParticipant calls **REAL HSM simulator**
6. HSM decrypts PIN block using TPK
7. HSM compares with stored PIN block under LMK
8. Response code 00 (approved) or 55 (invalid PIN)

**Expected Result:**
- Response MTI: 0210
- Response Code: 00 (if PIN correct)
- Field 54: Balance information

### 2. Balance Inquiry without PIN

**Test:** `testBalanceInquiryWithoutPin()`

**Flow:**
1. Send ISO-8583 balance inquiry without field 52
2. PIN verification is skipped
3. Database returns account balance
4. Response code 00 (approved)

**Expected Result:**
- Response MTI: 0210
- Response Code: 00
- Field 54: Balance information

### 3. Withdrawal with PIN

**Test:** `testWithdrawalWithPinIntegration()`

**Flow:**
1. Generate 3DES PIN block
2. Send ISO-8583 withdrawal (MTI 0200, processing code 010000)
3. PIN verification via HSM
4. Database transaction (debit account)
5. Response with reference number

**Expected Result:**
- Response MTI: 0210
- Response Code: 00 (if sufficient funds)
- Field 37: Retrieval reference number

### 4. Invalid Account

**Test:** `testBalanceInquiryInvalidAccount()`

**Flow:**
1. Request for non-existent account
2. Database returns not found
3. Response code 14

**Expected Result:**
- Response MTI: 0210
- Response Code: 14 (invalid account)

### 5. Insufficient Funds

**Test:** `testWithdrawalInsufficientFunds()`

**Flow:**
1. Request withdrawal exceeding balance
2. Business validation fails
3. Response code 51

**Expected Result:**
- Response MTI: 0210
- Response Code: 51 (insufficient funds)

## Troubleshooting

### Issue: Tests Fail with "Connection Refused"

**Cause:** HSM simulator not running

**Solution:**
```bash
# Check if HSM is running
curl http://localhost:8080/actuator/health

# If not, start HSM simulator
cd hsm-simulator
./mvnw spring-boot:run
```

### Issue: Tests Fail with "Invalid PIN" (Response Code 55)

**Cause:** PIN block mismatch or keys not configured

**Solution:**
1. Verify TPK key matches in HSM and test configuration
2. Check stored PIN block in database
3. Verify account 1234567890 exists with correct PIN data

```bash
# Check database
psql -h localhost -U bankuser -d bankdb -c \
  "SELECT account_number, encrypted_pin_block, pvv FROM accounts WHERE account_number='1234567890';"
```

### Issue: Tests Fail with "System Error" (Response Code 96)

**Cause:** HSM communication error or configuration issue

**Solution:**
1. Check HSM logs for errors
2. Verify application-test.properties configuration:
   - `hsm.url=http://localhost:8080`
   - `hsm.pin.terminal-id=ATM00001`
3. Check network connectivity

### Issue: Integration Tests Are Skipped

**Cause:** Environment variable not set

**Solution:**
```bash
export INTEGRATION_TEST=true
mvn test -Dtest=JposServerIntegrationTest
```

## Test Configuration

### application-test.properties

```properties
# HSM Configuration for Testing
hsm.pin.encryption-algorithm=AES_256
hsm.mac.verify-enabled=false
hsm.mac.generate-enabled=false

# Test Terminal Configuration (matches HSM sample data)
test.terminal.id=ATM-001
test.institution.id=TRM-ISS001

# Test TPK (from HSM sample data - TPK-TRM-ISS001-ATM-001)
# 32 bytes (256 bits) = 64 hex characters
test.tpk.key=246A31D729B280DD7FCDA3BB7F187ABFA1BB0811D7EF3D68FDCA63579F3748B0
```

### Configuration Details

**Terminal Identification:**
- Field 41 (`test.terminal.id`): `ATM-001`
- Field 42 (`test.institution.id`): `TRM-ISS001`
- Combined for HSM: `TRM-ISS001-ATM-001`

**TPK Key:**
- From HSM sample data: `TPK-TRM-ISS001-ATM-001`
- Algorithm: AES-256 (32 bytes = 64 hex characters)
- PIN blocks in field 123 (16 bytes, regardless of key size)

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      hsm-simulator:
        image: artivisi/hsm-simulator:latest
        ports:
          - 8080:8080
        options: >-
          --health-cmd "curl -f http://localhost:8080/actuator/health"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Wait for HSM
        run: |
          timeout 60 bash -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'

      - name: Run Integration Tests
        run: mvn test
```

## Test Data Setup

### Minimal Test Data

For integration tests to pass, you need:

**Account Table:**
```sql
INSERT INTO accounts (account_number, account_holder_name, balance, currency,
                     account_type, status, encrypted_pin_block, pvv)
VALUES ('1234567890', 'Test User', 5000000.00, 'IDR',
        'SAVINGS', 'ACTIVE',
        'ABCD1234567890ABCDEF1234567890AB',  -- Encrypted PIN block under LMK
        '1234');  -- PVV for PIN 1234
```

**Generate Encrypted PIN Block:**
```bash
# Use HSM simulator to generate test PIN block
curl -X POST http://localhost:8080/api/hsm/pin/generate-pinblock \
  -H "Content-Type: application/json" \
  -d '{
    "clearPin": "1234",
    "accountNumber": "4111111111111111",
    "pinFormat": "ISO-0",
    "lmkKeyId": "LMK-BANK-001"
  }'
```

## Summary

### Quick Start

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Start HSM simulator (has pre-seeded sample data)
cd hsm-simulator && ./mvnw spring-boot:run &

# 3. Run integration tests
mvn test

# 4. Check results
# Look for "TEST PASSED" in logs
```

**That's it!** No HSM setup or environment variables needed - tests use HSM's pre-configured sample data.

### Test Results Interpretation

| Response Code | Meaning | Test Status |
|---------------|---------|-------------|
| 00 | Approved | ✅ Pass |
| 14 | Invalid account | ✅ Pass (expected) |
| 30 | Format error | ❌ Fail (config issue) |
| 51 | Insufficient funds | ✅ Pass (expected) |
| 55 | Incorrect PIN | ⚠️ Check PIN/key config |
| 62 | Restricted account | ⚠️ Check account status |
| 96 | System error | ❌ Fail (HSM or network issue) |

## Next Steps

1. ✅ AES-256 PIN blocks (field 123) - using HSM sample data
2. ⚠️ Add tests for PVV verification method
3. ⚠️ Add tests for MAC verification
4. ⚠️ Add performance tests with concurrent requests

## Technical Notes

**Terminal ID Handling:**
- Field 41 (16 bytes): Terminal ID (e.g., `ATM-001`)
- Field 42 (15 bytes): Card Acceptor ID / Institution (e.g., `TRM-ISS001`)
- Application combines: `field42 + "-" + field41` → `TRM-ISS001-ATM-001`
- This matches HSM's 19-character terminal IDs while staying within ISO-8583 field limits

**Encryption:**
- Tests use AES-256 TPK from HSM sample data
- PIN blocks in field 123 (16 bytes, private use field, LLLCHAR format)
- AES-256 produces same 16-byte block as AES-128
- Field 52 (8 bytes) used for legacy 3DES support

## References

- [HSM Simulator Documentation](https://github.com/artivisi/hsm-simulator)
- [ISO-8583 Specification](README.md#jpos-iso-8583-server)
- [jPOS Documentation](https://jpos.org/doc/)
