# Hari 3 – Alur Pembayaran Tagihan End-to-End

## Tujuan
- Implementasi alur pembayaran lengkap dari Client → Acquirer → Gateway → Billing
- Layanan translasi JSON ↔ ISO-8583
- Manajemen state transaksi
- Penanganan error dan pemetaan respons
- Pengujian dan debugging kolaboratif

## 1. Arsitektur Alur Pembayaran Lengkap

### 1.1 Alur Transaksi End-to-End
```mermaid
sequenceDiagram
    participant Client
    participant Acquirer
    participant Gateway
    participant Billing
    participant Database

    Client->>Acquirer: HTTP POST /payment/request (JSON)
    Note over Client,Acquirer: Payment request dengan rincian tagihan

    Acquirer->>Acquirer: Validasi request
    Acquirer->>Database: Buat transaksi (PENDING)
    Acquirer->>Gateway: Konversi JSON → ISO-8583
    Note over Acquirer,Gateway: MTI 0200 + DE 48 (data tagihan)

    Gateway->>Gateway: Validasi ISO-8583
    Gateway->>Gateway: Route ke Billing Provider
    Gateway->>Billing: TCP/ISO-8583 (MTI 0200)

    Billing->>Billing: Konversi ISO-8583 → Business Object
    Billing->>Database: Validasi rincian tagihan
    Billing->>Database: Proses pembayaran
    Billing->>Database: Update status tagihan (PAID)
    Billing->>Gateway: Respons ISO-8583 (MTI 0210)
    Note over Billing,Gateway: DE 39 = 00 (Success)

    Gateway->>Acquirer: Forward respons
    Acquirer->>Database: Update transaksi (SUCCESS)
    Acquirer->>Client: HTTP Response (JSON)
```

### 1.2 Titik Integrasi Layanan
```mermaid
graph TB
    subgraph "Acquirer Service :8080"
        A1[REST API]
        A2[Business Service]
        A3[JSON → ISO-8583]
        A4[Transaction Manager]
    end

    subgraph "Gateway :8081"
        G1[ISO-8583 Receiver]
        G2[Message Router]
        G3[Switch Logic]
        G4[ISO-8583 Sender]
    end

    subgraph "Billing Provider :8082"
        B1[ISO-8583 Receiver]
        B2[Bill Service]
        B3[Payment Processor]
        B4[Response Generator]
    end

    A1 --> A2
    A2 --> A3
    A3 --> G1
    G1 --> G2
    G2 --> G3
    G3 --> G4
    G4 --> B1
    B1 --> B2
    B2 --> B3
    B3 --> B4
    B4 --> G2
    G2 --> A4
    A4 --> A1

    A4 -.->|Database| DB
    B3 -.->|Database| DB

    style A3 fill:#e8f5e8
    style G1 fill:#e8f5e8
    style G4 fill:#e8f5e8
    style B1 fill:#e8f5e8
    style B4 fill:#e8f5e8
```

## 2. Translasi JSON ↔ ISO-8583

### 2.1 Arsitektur Layanan Translasi
```mermaid
graph LR
    A[JSON Request] --> B[Translation Service]
    B --> C[ISO-8583 Message]

    subgraph "Translation Components"
        D[Field Mapper]
        E[Type Converter]
        F[Validator]
        G[Packager]
    end

    B --> D
    D --> E
    E --> F
    F --> G

    style B fill:#e1f5fe
    style G fill:#f3e5f5
```

### 2.2 Konfigurasi Pemetaan Field
```json
{
  "requestMapping": {
    "billId": "DE_48_SUBFIELD_1",
    "customerId": "DE_48_SUBFIELD_2",
    "amount": "DE_4",
    "currency": "DE_49",
    "transactionId": "DE_11",
    "timestamp": "DE_12_DE_13",
    "merchantType": "DE_18",
    "acquirerInstitution": "DE_32",
    "retrievalReference": "DE_37",
    "cardAcceptor": "DE_43"
  },
  "responseMapping": {
    "transactionId": "DE_11",
    "responseCode": "DE_39",
    "authorizationId": "DE_38",
    "responseTime": "DE_12_DE_13",
    "retrievalReference": "DE_37",
    "additionalAmounts": "DE_54"
  }
}
```

### 2.3 Tugas Implementasi
Peserta akan mengimplementasikan:
- **FieldMapperService** untuk mapping field JSON ↔ ISO-8583
- **TypeConverterService** untuk konversi tipe data
- **ValidationService** untuk validasi field
- **TranslationService** untuk konversi end-to-end

## 3. Manajemen State Transaksi

### 3.1 Alur State Transaksi
```mermaid
stateDiagram-v2
    [*] --> PENDING: Request Received
    PENDING --> PROCESSING: Validated
    PENDING --> FAILED: Validation Error

    PROCESSING --> SUCCESS: Payment Complete
    PROCESSING --> FAILED: Processing Error
    PROCESSING --> TIMEOUT: No Response

    FAILED --> PENDING: Retry Allowed
    TIMEOUT --> PENDING: Retry Logic

    SUCCESS --> [*]: Complete
    FAILED --> [*]: Max Retries Reached

    note right of PROCESSING
        ISO-8583 Message Sent
        Waiting for Response
        Timeout Monitoring
    end note
```

### 3.2 Implementasi Manajemen State
**Peserta akan membuat:**
- **TransactionStateService** untuk transisi state
- **TimeoutManager** untuk penanganan timeout
- **RetryService** untuk logika retry
- **StateRepository** untuk persistence

### 3.3 Konfigurasi Timeout Transaksi
```yaml
transaction:
  timeout:
    processing: 30s      # Waktu maksimal untuk proses pembayaran
    response: 45s        # Waktu maksimal untuk respons
    retry: 60s           # Waktu tunggu sebelum retry
  retry:
    maxAttempts: 3       # Maksimal percobaan retry
    backoffMultiplier: 2 # Exponential backoff
```

## 4. Penanganan Error & Pemetaan Respons

### 4.1 Pemetaan Kode Respons ISO-8583
```mermaid
graph LR
    A[ISO-8583 Response Codes] --> B[HTTP Status Mapping]
    B --> C[JSON Response Format]

    A1["DE 39 = 00"] --> B1["200 OK"]
    A2["DE 39 = 05"] --> B2["400 Bad Request"]
    A3["DE 39 = 14"] --> B3["404 Not Found"]
    A4["DE 39 = 91"] --> B4["503 Service Unavailable"]

    B1 --> C1["Success Response"]
    B2 --> C2["Invalid Data Error"]
    B3 --> C3["Not Found Error"]
    B4 --> C4["Service Error"]
```

### 4.2 Format Respons Error Standar
```json
{
  "transactionId": "TXN20251021001",
  "status": "FAILED",
  "responseCode": "05",
  "message": "Do not honor",
  "details": {
    "errorCode": "PAYMENT_DECLINED",
    "originalError": "Insufficient funds",
    "timestamp": "2025-10-21T09:15:35Z",
    "retryAllowed": false
  }
}
```

### 4.3 Kategorisasi Error
**Peserta akan mengimplementasikan penanganan error untuk:**
- **Business Logic Errors**: Tagihan tidak valid, dana tidak cukup
- **Network Errors**: Koneksi timeout, host tidak bisa dijangkau
- **System Errors**: Database failure, error pemrosesan
- **Security Errors**: MAC tidak valid, token kedaluwarsa

## 5. Pengujian & Debugging

### 5.1 Skenario Pengujian
```mermaid
graph TB
    A[Happy Path] --> A1[Successful Payment]
    B[Error Scenarios] --> B1[Invalid Bill]
    B --> B2[Insufficient Funds]
    B --> B3[Network Timeout]
    B --> B4[System Error]
    C[Edge Cases] --> C1[Duplicate Request]
    C --> C2[Invalid Amount]
    C --> C3[Expired Bill]
    C --> C4[Concurrent Payment]

    A1 --> Test1[Verify Success Response]
    B1 --> Test2[Verify Error Response]
    B2 --> Test3[Verify Declined Response]
    B3 --> Test4[Verify Timeout Handling]
    B4 --> Test5[Verify Error Recovery]
    C1 --> Test6[Verify Idempotency]
    C2 --> Test7[Verify Validation]
    C3 --> Test8[Verify Business Rules]
    C4 --> Test9[Verify Concurrency]
```

### 5.2 Script Pengujian Integrasi
**Peserta akan membuat script pengujian untuk:**
- **Alur pembayaran normal** dengan berbagai tipe tagihan
- **Skenario error** dengan kode respons berbeda
- **Penanganan timeout** dengan respons yang ditunda
- **Request konkuren** dengan ID transaksi duplikat

### 5.3 Implementasi Alat Debug
**Peserta akan membangun:**
- **Message Tracer** untuk visualisasi alur transaksi
- **Log Aggregator** untuk logging terpusat
- **Response Validator** untuk validasi format
- **Health Check Service** untuk status sistem

## 6. Contoh Data Pengujian

### 6.1 Data Tagihan Pengujian
Data tagihan pengujian tersedia di: `data/test-bills.sql`

### 6.2 Skenario Transaksi Pengujian
Skenario transaksi pengujian tersedia di: `scenarios/transaction-tests.json`

### 6.3 Contoh Respons yang Diharapkan
Contoh respons yang diharapkan tersedia di: `samples/expected-responses.json`

## 7. Validasi Implementasi

### 7.1 Pengujian End-to-End
```bash
# Test complete payment flow
curl -X POST http://localhost:8080/api/v1/payment/request \
  -H "Content-Type: application/json" \
  -d @samples/payment-request.json

# Monitor transaction flow
curl http://localhost:8081/api/v1/admin/trace/TXN20251021001

# Verify bill status
curl http://localhost:8082/api/v1/bill/status/BILL001
```

### 7.2 Checklist Validasi
- [ ] Alur pembayaran lengkap berfungsi
- [ ] Translasi JSON → ISO-8583 berfungsi
- [ ] Manajemen state transaksi berfungsi
- [ ] Penanganan error diimplementasikan dengan benar
- [ ] Pemetaan respons akurat
- [ ] Penanganan timeout berfungsi
- [ ] Logika retry berfungsi
- [ ] Idempotency diimplementasikan
- [ ] Penanganan konkurensi aman
- [ ] Semua skenario pengujian berhasil

### 7.3 Validasi Performa
- **Response time** < 2 detik untuk alur normal
- **Throughput** > 100 transaksi/menit
- **Error rate** < 1% untuk operasi normal
- **Recovery time** < 30 detik untuk kegagalan

## 8. Panduan Pemecahan Masalah

### 8.1 Masalah Umum
- **Translation errors**: Konfigurasi pemetaan field
- **State management**: Konsistensi database
- **Timeout issues**: Konektivitas jaringan
- **Error handling**: Pemetaan kode respons

### 8.2 Perintah Debug
```bash
# Check transaction status
docker-compose exec postgres psql -U postgres -d payment_system \
  -c "SELECT * FROM transactions WHERE transaction_id = 'TXN20251021001';"

# Monitor JPos messages
tail -f logs/q2.log | grep "TXN20251021001"

# Test ISO-8583 message directly
curl -X POST http://localhost:8081/api/v1/iso/test \
  -H "Content-Type: application/json" \
  -d @samples/iso-test.json
```

## 9. Langkah Selanjutnya

Setelah berhasil menyelesaikan Day 3:
1. Alur pembayaran lengkap diimplementasikan dan diuji
2. Translasi JSON ↔ ISO-8583 berfungsi
3. Manajemen state transaksi robust
4. Penanganan error komprehensif
5. Siapkan untuk Day 4 (Integrasi Keamanan HSM)
6. Review konsep PIN, MAC, dan Key Exchange