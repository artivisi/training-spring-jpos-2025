# Integration Test Guide

## Overview

This project includes **real integration tests** that communicate with the actual HSM simulator. No mocking is used - these tests verify end-to-end functionality.

## Test Types

### 1. Unit Tests (Mocked)
- **File:** `JposServerTest.java`
- **Uses:** `@MockitoBean` for HSM client
- **Purpose:** Fast tests without external dependencies
- **Run:** `mvn test` (default)

### 2. Integration Tests (Real HSM)
- **File:** `JposServerIntegrationTest.java`
- **Uses:** Real HSM simulator on http://localhost:8080
- **Purpose:** End-to-end validation with actual HSM
- **Run:** Requires environment variable (see below)

## Prerequisites for Integration Tests

### 1. Start PostgreSQL (or uses H2 in-memory)

The integration tests use H2 in-memory database by default, so no PostgreSQL needed for tests.

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

### 3. Configure Test Keys in HSM

The integration tests require specific keys to be configured in the HSM simulator:

#### TPK (Terminal PIN Key)
```bash
curl -X POST http://localhost:8080/api/hsm/keys \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "TPK",
    "keyId": "TPK-TEST-001",
    "terminalId": "ATM00001",
    "keyValue": "0123456789ABCDEFFEDCBA9876543210"
  }'
```

#### LMK (Local Master Key)
```bash
curl -X POST http://localhost:8080/api/hsm/keys \
  -H "Content-Type: application/json" \
  -d '{
    "keyType": "LMK",
    "keyId": "LMK-BANK-001"
  }'
```

### 4. Configure Test Account PIN in Database

The integration tests expect account `1234567890` to have:
- **Clear PIN:** `1234`
- **Stored encrypted PIN block** or **PVV** in the database

**Generate and store encrypted PIN block:**
```bash
# Step 1: Encrypt PIN with TPK
curl -X POST http://localhost:8080/api/hsm/pin/encrypt \
  -H "Content-Type: application/json" \
  -d '{
    "keyId": "TPK-TEST-001",
    "accountNumber": "4111111111111111",
    "clearPin": "1234",
    "pinFormat": "ISO-0"
  }'

# Response will include:
# - encryptedPinBlock: "ABCD1234567890AB"
# - pinVerificationValue: "1234"

# Step 2: Store in database
# Update account 1234567890:
# - encrypted_pin_block = "ABCD1234567890AB"
# - pvv = "1234"
```

## Running Integration Tests

### Run All Tests (Unit + Integration)

```bash
# Set environment variable to enable integration tests
export INTEGRATION_TEST=true

# Run all tests
mvn test

# Or specify the integration test class
mvn test -Dtest=JposServerIntegrationTest
```

### Run Only Unit Tests (Fast)

```bash
# Default - no environment variable needed
mvn test

# Integration tests are skipped automatically
# (annotated with @EnabledIfEnvironmentVariable)
```

### Run Specific Integration Test

```bash
export INTEGRATION_TEST=true

# Run specific test method
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
hsm.url=http://localhost:8080
hsm.pin.encrypted-pin-block.endpoint=/api/hsm/pin/verify-with-translation
hsm.pin.pvv.endpoint=/api/hsm/pin/verify-with-pvv
hsm.pin.terminal-id=ATM00001
hsm.pin.format=ISO_0
hsm.pin.encryption-algorithm=TDES
hsm.mac.algorithm=AES_CMAC
hsm.mac.verify-enabled=false
hsm.mac.generate-enabled=false

# Test TPK (must match HSM configuration)
test.tpk.key=0123456789ABCDEFFEDCBA9876543210
```

### Key Configuration Match

**CRITICAL:** The test TPK must match the TPK configured in HSM simulator.

**Test Configuration:**
- `test.tpk.key=0123456789ABCDEFFEDCBA9876543210`

**HSM Configuration:**
- TPK Key ID: `TPK-TEST-001`
- Terminal ID: `ATM00001`
- Key Value: `0123456789ABCDEFFEDCBA9876543210`

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

      - name: Configure HSM Keys
        run: |
          ./scripts/setup-hsm-test-keys.sh

      - name: Run Integration Tests
        env:
          INTEGRATION_TEST: true
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
# 1. Start HSM simulator
cd hsm-simulator && ./mvnw spring-boot:run &

# 2. Configure test keys
curl -X POST http://localhost:8080/api/hsm/keys -H "Content-Type: application/json" \
  -d '{"keyType":"TPK","keyId":"TPK-TEST-001","terminalId":"ATM00001","keyValue":"0123456789ABCDEFFEDCBA9876543210"}'

# 3. Run integration tests
export INTEGRATION_TEST=true
mvn test -Dtest=JposServerIntegrationTest

# 4. Check results
# Look for "TEST PASSED" in logs
```

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

1. Set up automated test data generation
2. Add tests for AES-128 PIN blocks (field 123)
3. Add tests for PVV verification method
4. Add tests for MAC verification
5. Add performance tests with concurrent requests

## References

- [HSM Simulator Documentation](https://github.com/artivisi/hsm-simulator)
- [ISO-8583 Specification](README.md#jpos-iso-8583-server)
- [jPOS Documentation](https://jpos.org/doc/)
