# Hari 3 – End-to-End ATM Transaction Flow

## Tujuan
- Integrasi jPOS dengan Spring Boot (Transaction Participants)
- Implementasi alur transaksi ATM end-to-end (Balance Inquiry & Cash Withdrawal)
- Translasi Web UI ↔ ISO-8583
- Processing Code, PAN, Amount, STAN handling
- Message field mapping dan response handling
- Collaborative testing & debugging

## 1. Arsitektur Alur Transaksi ATM

### 1.1 Alur Transaksi End-to-End
```mermaid
sequenceDiagram
    participant User
    participant ATM UI
    participant ATM Service
    participant QMUX
    participant Bank Server
    participant Transaction Participant
    participant Account Service
    participant Database

    User->>ATM UI: Input card + PIN + select transaction
    ATM UI->>ATM Service: Process transaction request

    ATM Service->>ATM Service: Build ISO-8583 message
    Note over ATM Service: MTI 0200, Processing Code,<br/>PAN, Amount, STAN

    ATM Service->>QMUX: Send ISO-8583 request
    QMUX->>Bank Server: TCP/ISO-8583 (MTI 0200)

    Bank Server->>Transaction Participant: Route to handler
    Transaction Participant->>Account Service: Process transaction
    Account Service->>Database: Check balance / Update balance

    Account Service->>Transaction Participant: Return result
    Transaction Participant->>Transaction Participant: Build ISO-8583 response
    Note over Transaction Participant: MTI 0210, DE 39 (Response Code)

    Transaction Participant->>Bank Server: Response ready
    Bank Server->>QMUX: TCP/ISO-8583 (MTI 0210)
    QMUX->>ATM Service: Match response with request

    ATM Service->>ATM UI: Display result
    ATM UI->>User: Show balance / dispense cash
```

### 1.2 Komponen Sistem
```mermaid
graph TB
    subgraph "ATM Simulator :7070"
        U1[Web UI - Thymeleaf]
        A1[ATM Controller]
        A2[ATM Service]
        A3[ISO-8583 Message Builder]
        A4[QMUX Client]
    end

    subgraph "Bank Server :9090"
        S1[QServer :22222]
        S2[TransactionManager]
        S3[Transaction Participants]
        S4[Account Service]
        S5[Transaction Service]
    end

    DB[(PostgreSQL<br/>Accounts & Transactions)]

    U1 --> A1
    A1 --> A2
    A2 --> A3
    A3 --> A4
    A4 -->|TCP/ISO-8583| S1
    S1 --> S2
    S2 --> S3
    S3 --> S4
    S4 --> S5
    S5 --> DB

    style A3 fill:#e8f5e8
    style A4 fill:#e1f5fe
    style S1 fill:#f3e5f5
    style S3 fill:#fff3e0
```

## 2. ISO-8583 Message Field Mapping

### 2.1 Balance Inquiry Message Fields
```mermaid
graph LR
    subgraph "Request MTI 0200"
        R2[DE 2: PAN]
        R3[DE 3: Processing Code = 310000]
        R11[DE 11: STAN]
        R41[DE 41: Terminal ID]
        R42[DE 42: Institution ID]
    end

    subgraph "Response MTI 0210"
        P2[DE 2: PAN echo]
        P3[DE 3: Processing Code echo]
        P11[DE 11: STAN echo]
        P39[DE 39: Response Code]
        P54[DE 54: Additional Amounts = Balance]
    end

    style R3 fill:#e8f5e8
    style P39 fill:#f3e5f5
    style P54 fill:#fff3e0
```

### 2.2 Withdrawal Message Fields
```mermaid
graph LR
    subgraph "Request MTI 0200"
        R2[DE 2: PAN]
        R3[DE 3: Processing Code = 010000]
        R4[DE 4: Amount]
        R11[DE 11: STAN]
        R41[DE 41: Terminal ID]
        R42[DE 42: Institution ID]
    end

    subgraph "Response MTI 0210"
        P2[DE 2: PAN echo]
        P3[DE 3: Processing Code echo]
        P4[DE 4: Amount echo]
        P11[DE 11: STAN echo]
        P39[DE 39: Response Code]
        P54[DE 54: Additional Amounts = Remaining Balance]
    end

    style R3 fill:#e8f5e8
    style R4 fill:#e1f5fe
    style P39 fill:#f3e5f5
    style P54 fill:#fff3e0
```

### 2.3 Processing Code Reference
| Transaction Type | Processing Code | Description |
|-----------------|-----------------|-------------|
| Balance Inquiry | 310000 | Inquiry into cardholder account |
| Cash Withdrawal | 010000 | Cash disbursement |

### 2.4 Tugas Implementasi
Peserta akan mengimplementasikan:

**ATM Simulator (ISO-8583 Message Builder):**
- **Iso8583MessageBuilder** untuk build request messages
- Set MTI, Processing Code, PAN, Amount, STAN
- Set Terminal ID dan Institution ID
- Generate timestamp fields (DE 12, DE 13)

**Bank Server (Transaction Participants):**
- **BalanceInquiryParticipant** untuk handle processing code 310000
- **WithdrawalParticipant** untuk handle processing code 010000
- **ResponseBuilder** untuk build ISO-8583 responses
- Field validation dan business logic integration

## 3. Transaction Participants Implementation

### 3.1 Transaction Participant Flow
```mermaid
graph TB
    A[ISO-8583 Message Received] --> B[TransactionManager]
    B --> C{Check Processing Code}

    C -->|310000| D[BalanceInquiryParticipant]
    C -->|010000| E[WithdrawalParticipant]
    C -->|Unknown| F[UnknownTransactionParticipant]

    D --> G[AccountService]
    E --> G

    G --> H[Build Response]
    H --> I[Set DE 39 Response Code]
    H --> J[Set DE 54 Balance]

    I --> K[Return Response]
    J --> K

    F --> L[Set DE 39 = 12 Invalid Transaction]
    L --> K

    style D fill:#e8f5e8
    style E fill:#e1f5fe
    style G fill:#f3e5f5
    style I fill:#fff3e0
```

### 3.2 Implementation Tasks
**Peserta akan membuat:**

**BalanceInquiryParticipant:**
- Implement `org.jpos.transaction.TransactionParticipant`
- Override `prepare()` method
- Extract PAN from DE 2
- Call AccountService to get balance
- Build response dengan DE 39 = 00 dan DE 54 = balance
- Handle account not found (DE 39 = 14)

**WithdrawalParticipant:**
- Implement `org.jpos.transaction.TransactionParticipant`
- Override `prepare()` method
- Extract PAN from DE 2, Amount from DE 4
- Call AccountService to process withdrawal
- Build response dengan DE 39 = 00 dan DE 54 = remaining balance
- Handle insufficient funds (DE 39 = 51)
- Handle account not found (DE 39 = 14)

**TransactionManager Configuration:**
- Define participant groups in `20_txnmgr.xml`
- Route by processing code
- Configure timeout (30 seconds default)
- Enable Spring bean injection

## 4. Response Code Mapping

### 4.1 ISO-8583 Response Codes untuk ATM
| Response Code (DE 39) | Description | Use Case |
|----------------------|-------------|----------|
| 00 | Approved | Transaction successful |
| 05 | Do not honor | Generic decline |
| 12 | Invalid transaction | Unknown processing code |
| 14 | Invalid card number | Account not found |
| 51 | Insufficient funds | Balance too low for withdrawal |
| 55 | Incorrect PIN | PIN verification failed |
| 91 | System malfunction | Terminal not signed on |
| 96 | System error | Database or processing error |

### 4.2 ATM UI Display Mapping
```java
// Response code to user message mapping
Map<String, String> messageMap = Map.of(
    "00", "Transaction successful",
    "05", "Transaction declined",
    "12", "Invalid transaction type",
    "14", "Invalid account number",
    "51", "Insufficient funds",
    "55", "Incorrect PIN",
    "91", "Service temporarily unavailable",
    "96", "System error, please try again"
);
```

### 4.3 Error Handling Implementation
**Peserta akan mengimplementasikan:**

**ATM Simulator:**
- Parse response code dari DE 39
- Map response code ke user-friendly message
- Display appropriate message pada web UI
- Log transaction result

**Bank Server:**
- Catch exceptions dalam Transaction Participants
- Map exceptions ke appropriate response codes
- Log errors untuk troubleshooting
- Return proper ISO-8583 response dengan error code

## 5. Testing & Debugging

### 5.1 Test Scenarios
```mermaid
graph TB
    A[Happy Path] --> A1[Successful Balance Inquiry]
    A --> A2[Successful Withdrawal]

    B[Error Scenarios] --> B1[Invalid Account]
    B --> B2[Insufficient Funds]
    B --> B3[Terminal Not Signed On]
    B --> B4[System Error]

    C[Edge Cases] --> C1[Zero Amount Withdrawal]
    C --> C2[Very Large Amount]
    C --> C3[Concurrent Transactions]

    A1 --> Test1[Verify DE 39 = 00, DE 54 = Balance]
    A2 --> Test2[Verify DE 39 = 00, Balance Updated]
    B1 --> Test3[Verify DE 39 = 14]
    B2 --> Test4[Verify DE 39 = 51]
    B3 --> Test5[Verify DE 39 = 91]
    B4 --> Test6[Verify DE 39 = 96]
```

### 5.2 Testing via Web UI
```bash
# Open ATM UI
open http://localhost:7070

# Test Balance Inquiry:
1. Enter card number: 1234567890
2. Enter PIN: 1234
3. Select: Balance Inquiry
4. Click Execute
5. Expected: "Your balance is: 5,000,000"

# Test Withdrawal:
1. Enter card number: 1234567890
2. Enter PIN: 1234
3. Select: Withdrawal
4. Enter amount: 500000
5. Click Execute
6. Expected: "Withdrawal successful. Remaining balance: 4,500,000"

# Test Insufficient Funds:
1. Enter card number: 1234567890
2. Enter amount: 10000000 (more than balance)
3. Expected: "Insufficient funds"

# Test Invalid Account:
1. Enter card number: 9999999999
2. Expected: "Invalid account number"
```

### 5.3 Testing via Logs
**Monitor ATM Simulator:**
```bash
tail -f logs/atm-simulator.log

# Expected for Balance Inquiry:
[INFO] Building ISO-8583 message: MTI=0200, Processing Code=310000
[INFO] Sending request via QMUX, STAN=000001
[INFO] Received response: MTI=0210, DE39=00
[INFO] Balance: 5000000
```

**Monitor Bank Server:**
```bash
tail -f logs/bank-server.log

# Expected for Balance Inquiry:
[INFO] Received message: MTI=0200, Processing Code=310000, PAN=1234567890
[INFO] Routing to BalanceInquiryParticipant
[INFO] Account balance: 5000000
[INFO] Sending response: MTI=0210, DE39=00, DE54=5000000
```

### 5.4 Database Verification
```sql
-- Check transaction logs
SELECT * FROM transactions
ORDER BY created_at DESC
LIMIT 10;

-- Check account balance updates
SELECT account_number, balance, updated_at
FROM accounts
WHERE account_number = '1234567890';

-- Verify transaction count
SELECT transaction_type, COUNT(*) as count
FROM transactions
GROUP BY transaction_type;
```

## 6. Sample Test Data

### 6.1 Test Accounts (Already in database)
| Account Number | Holder Name | Balance | PIN |
|---------------|-------------|---------|-----|
| 1234567890 | John Doe | 5,000,000 | 1234 |
| 0987654321 | Jane Smith | 3,000,000 | 5678 |
| 5555555555 | Bob Johnson | 10,000,000 | 9999 |

### 6.2 Sample ISO-8583 Messages

**Balance Inquiry Request:**
```
MTI: 0200
DE 2: 1234567890
DE 3: 310000
DE 11: 000001
DE 41: ATM-001
DE 42: TRM-ISS001
```

**Balance Inquiry Response:**
```
MTI: 0210
DE 2: 1234567890
DE 3: 310000
DE 11: 000001
DE 39: 00
DE 54: 5000000
```

**Withdrawal Request:**
```
MTI: 0200
DE 2: 1234567890
DE 3: 010000
DE 4: 500000
DE 11: 000002
DE 41: ATM-001
DE 42: TRM-ISS001
```

**Withdrawal Response:**
```
MTI: 0210
DE 2: 1234567890
DE 3: 010000
DE 4: 500000
DE 11: 000002
DE 39: 00
DE 54: 4500000
```

## 7. Validation Checklist

### 7.1 End-to-End Testing
- [ ] ATM Web UI accessible pada http://localhost:7070
- [ ] Bank Server listening pada port 22222
- [ ] jPOS Q2 Server running without errors
- [ ] QMUX connection established
- [ ] Terminal signed on automatically

### 7.2 Transaction Testing
- [ ] Balance inquiry berfungsi (Processing Code 310000)
- [ ] Withdrawal berfungsi (Processing Code 010000)
- [ ] Response code 00 untuk successful transactions
- [ ] Response code 14 untuk invalid account
- [ ] Response code 51 untuk insufficient funds
- [ ] Balance updated correctly dalam database
- [ ] Transaction logs created

### 7.3 Integration Testing
- [ ] ISO-8583 messages built correctly (MTI, DE 2, DE 3, DE 4, DE 11)
- [ ] Transaction Participants routing by processing code
- [ ] AccountService integration berfungsi
- [ ] Database transactions logged
- [ ] Response messages properly formatted
- [ ] STAN correlation working (request-response matching)

### 7.4 Performance Validation
- **Response time** < 1 detik untuk alur normal
- **QMUX timeout** configured (30 detik)
- **Database queries** optimized
- **Concurrent transactions** handled correctly

## 8. Troubleshooting

### 8.1 Common Issues

**Transaction Participant Not Found:**
```bash
# Check TransactionManager configuration
cat src/main/resources/deploy/20_txnmgr.xml

# Check participant routing
tail -f logs/bank-server.log | grep "participant"
```

**Response Code Always 96 (System Error):**
```bash
# Check exception logs
tail -f logs/bank-server.log | grep "Exception"

# Check database connection
docker-compose exec postgres psql -U postgres -d payment_system -c "SELECT 1;"
```

**QMUX Timeout:**
```bash
# Check QMUX configuration
grep "timeout" src/main/resources/deploy/15_mux.xml

# Check connection to server
telnet localhost 22222

# Monitor QMUX logs
tail -f logs/atm-simulator.log | grep "QMUX\|timeout"
```

**Balance Not Updated:**
```bash
# Check optimistic locking version
SELECT id, account_number, balance, version
FROM accounts
WHERE account_number = '1234567890';

# Check transaction logs
SELECT * FROM transactions
WHERE account_id IN (SELECT id FROM accounts WHERE account_number = '1234567890')
ORDER BY created_at DESC;
```

## 9. Next Steps

Setelah berhasil menyelesaikan Day 3:
1. Alur transaksi ATM end-to-end berfungsi (Balance Inquiry & Withdrawal)
2. jPOS Transaction Participants integrated dengan Spring Boot services
3. ISO-8583 message building dan parsing berfungsi
4. Processing Code routing berfungsi (310000 dan 010000)
5. Response code mapping implemented
6. Database integration untuk transaction logging
7. Siapkan untuk Day 4 (HSM Integration - PIN, MAC, Key Rotation)
8. Review konsep cryptography: PIN encryption, MAC generation, Key management