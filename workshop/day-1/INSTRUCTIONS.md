# Hari 1 – Spring Boot Application Setup, REST, dan Database

## Tujuan
- Pengenalan ekosistem pembayaran & ISO-8583
- Setup project Spring Boot (Acquirer & Billing Provider)
- Implementasi REST API untuk payment processing
- Integrasi database PostgreSQL & logging transaksi
- Testing dengan Postman untuk validasi API

## 1. Environment Setup

### 1.1 Docker & Docker Compose Setup
```bash
# Install Docker Desktop (untuk macOS/Windows)
# atau install Docker + Docker Compose (untuk Linux)

# Verifikasi instalasi
docker --version
docker-compose --version
```

### 1.2 Start PostgreSQL dengan Docker Compose
```bash
# Navigate ke workshop/day-1 directory
cd workshop/day-1

# Start PostgreSQL container
docker-compose up -d

# Verifikasi container running
docker-compose ps

# View logs
docker-compose logs -f postgres
```

### 1.3 Connect ke Database
```bash
# Connect ke container
docker-compose exec postgres psql -U postgres -d payment_system

# Atau connect dari host machine
psql -h localhost -p 5432 -U postgres -d payment_system
```

## 2. Database Schema

### 2.1 Database Structure
```mermaid
erDiagram
    TRANSACTIONS {
        bigint id PK
        varchar transaction_id UK
        decimal amount
        varchar currency
        varchar bill_id
        varchar customer_id
        varchar status
        timestamp created_at
        timestamp updated_at
        varchar response_code
        text error_message
    }

    BILLS {
        bigint id PK
        varchar bill_id UK
        varchar biller_id
        varchar customer_id
        varchar customer_name
        decimal amount
        decimal fee
        timestamp due_date
        varchar status
        text description
        timestamp created_at
        timestamp updated_at
    }

    AUDIT_LOGS {
        bigint id PK
        varchar transaction_id FK
        varchar action
        varchar module
        text details
        varchar user_id
        timestamp created_at
    }

    TRANSACTIONS ||--o{ AUDIT_LOGS : "has"
    BILLS ||--o{ TRANSACTIONS : "references"
```

### 2.2 Setup Database Schema
**Database akan dibuat otomatis oleh Docker Compose** karena script di folder `/docker-entrypoint-initdb.d` akan dieksekusi saat container pertama kali start.

Manual setup (jika diperlukan):
```bash
# Connect ke database
docker-compose exec postgres psql -U postgres -d payment_system

# Execute schema creation
\i /docker-entrypoint-initdb.d/schema.sql

# Execute sample data insertion
\i /docker-entrypoint-initdb.d/sample-data.sql
```

### 2.3 Verifikasi Database Setup
```bash
# Cek apakah tables sudah dibuat
docker-compose exec postgres psql -U postgres -d payment_system -c "\dt"

# Cek sample data
docker-compose exec postgres psql -U postgres -d payment_system -c "SELECT COUNT(*) FROM bills;"
docker-compose exec postgres psql -U postgres -d payment_system -c "SELECT COUNT(*) FROM transactions;"
```

## 3. Spring Boot Application Setup

### 3.1 Project Structure
```bash
# Buat struktur project untuk dua aplikasi
mkdir -p acquirer-service/src/main/java/com/training/acquirer
mkdir -p acquirer-service/src/main/resources
mkdir -p billing-provider/src/main/java/com/training/billing
mkdir -p billing-provider/src/main/resources
```

### 3.2 Spring Boot Dependencies
Peserta akan membuat dua project Spring Boot:

**Dependencies yang Diperlukan:**
- `spring-boot-starter-web` - Pengembangan REST API
- `spring-boot-starter-data-jpa` - Integrasi database
- `postgresql` - PostgreSQL JDBC driver
- `spring-boot-starter-validation` - Validasi request
- `spring-boot-starter-actuator` - Health check dan monitoring

**Tugas Implementasi:**
1. Buat file POM untuk acquirer-service dan billing-provider
2. Konfigurasi Spring Boot parent dependency
3. Tambah dependencies yang diperlukan untuk kedua service
4. Konfigurasi Maven compiler plugin untuk Java 25

### 3.3 Application Configuration
Peserta akan mengkonfigurasi file application.yml:

**Konfigurasi Acquirer Service:**
- Server port: 8080
- Application name: acquirer-service
- Database connection ke PostgreSQL
- Konfigurasi JPA dengan PostgreSQL dialect
- Logging level untuk debugging
- Actuator endpoints untuk health check

**Konfigurasi Billing Provider:**
- Server port: 8082
- Application name: billing-provider
- Database connection ke PostgreSQL
- Konfigurasi JPA untuk entity management

### 3.4 Domain Models
Peserta akan membuat JPA entities:

**Fitur Transaction Entity:**
- Annotations @Entity dan @Table
- Primary key dengan @GeneratedValue
- Field unique transaction ID
- BigDecimal amount dengan precision
- Enum transaction status (PENDING, SUCCESS, FAILED)
- Timestamp fields dengan @CreationTimestamp dan @UpdateTimestamp
- Validation annotations yang tepat

**Fitur Bill Entity:**
- Primary key dan unique bill ID
- Customer information fields
- Amount dan fee fields dengan precision yang tepat
- Enum bill status (ACTIVE, PAID, EXPIRED)
- Due date dan description fields
- Relationship annotations yang tepat

## 4. REST API Implementation

### 4.1 Acquirer Service API
Peserta akan implementasikan:

**PaymentRequest DTO:**
- Bean validation annotations (@NotBlank, @NotNull, @Positive)
- Required fields: billId, customerId, amount
- Optional fields: currency, customerEmail, customerPhone
- Getter/setter methods yang tepat

**Fitur PaymentController:**
- Annotations @RestController dan @RequestMapping
- Dependency injection dengan @Autowired
- @PostMapping untuk payment processing
- @GetMapping untuk status checking
- @Valid annotation untuk request validation
- Logging yang tepat dengan @Slf4j
- Health check endpoint dengan service information

### 4.2 Billing Provider API
Peserta akan implementasikan:

**Fitur BillController:**
- REST endpoints untuk bill inquiry
- Bill payment processing endpoint
- Bill validation endpoint
- Health check endpoint
- Request/response handling yang tepat
- Structured logging untuk debugging
- Error handling dengan HTTP status codes yang tepat

**DTOs yang Diperlukan:**
- BillResponse untuk inquiry results
- BillPaymentRequest untuk payment processing
- BillValidationRequest untuk bill validation
- BillValidationResponse untuk validation results

## 5. Service Layer Implementation

### 5.1 PaymentService
Peserta akan implementasikan:

**Fitur Core Service:**
- Annotations @Service dan @Transactional
- Transaction ID generation dengan timestamp
- Transaction record creation dan persistence
- Integration dengan billing provider
- Error handling dan transaction status updates

**Business Logic:**
- Transaction state management
- Billing provider communication
- Response mapping dan error handling
- Audit trail creation
- Exception handling yang tepat

**Integration Requirements:**
- TransactionRepository untuk data access
- BillServiceClient untuk inter-service communication
- Logging yang tepat untuk debugging dan monitoring
- Builder pattern untuk response construction

### 5.2 Repository Layer
Peserta akan membuat Spring Data repositories:

**Fitur TransactionRepository:**
- Spring Data JPA repository interface
- Custom query method untuk finding by transaction ID
- Exception handling yang tepat untuk not found cases

**Fitur BillRepository:**
- CRUD operations untuk bill entities
- Query methods untuk bill status dan customer ID
- Custom queries untuk business requirements

## 6. Application Startup
Peserta akan membuat main application classes:

**Fitur Spring Boot Application:**
- Annotation @SpringBootApplication
- Main method dengan SpringApplication.run()
- Component scanning configuration
- Package structure yang tepat

**Implementation Tasks:**
1. Buat AcquirerApplication class
2. Buat BillingProviderApplication class
3. Konfigurasi component scanning
4. Test application startup
5. Verify actuator endpoints bekerja

## 7. API Testing dengan Postman

### 7.1 Test Acquirer Service (Port 8080)
```http
# Payment Request
POST http://localhost:8080/api/v1/payment/request
Content-Type: application/json

{
    "billId": "BILL001",
    "customerId": "CUST001",
    "amount": 152500.00,
    "currency": "IDR",
    "customerEmail": "budi@email.com"
}

# Check Status
GET http://localhost:8080/api/v1/payment/status/TXN20251021001

# Health Check
GET http://localhost:8080/api/v1/health
```

### 7.2 Test Billing Provider (Port 8082)
```http
# Bill Inquiry
GET http://localhost:8082/api/v1/bill/inquiry/BILL001

# Process Payment
POST http://localhost:8082/api/v1/bill/payment
Content-Type: application/json

{
    "billId": "BILL001",
    "transactionId": "TXN20251021001",
    "amount": 152500.00
}

# Validate Bill
POST http://localhost:8082/api/v1/bill/validation
Content-Type: application/json

{
    "billId": "BILL001",
    "customerId": "CUST001"
}
```

## 8. Database Validation

### 8.1 Query Data untuk Testing
```sql
-- Check bills available
SELECT * FROM bills WHERE status = 'ACTIVE';

-- Check transactions
SELECT * FROM transactions ORDER BY created_at DESC;

-- Check audit logs
SELECT * FROM audit_logs ORDER BY created_at DESC;

-- Check bill payment status
SELECT b.bill_id, b.customer_id, b.amount, b.status,
       COUNT(t.id) as transaction_count
FROM bills b
LEFT JOIN transactions t ON b.bill_id = t.bill_id
GROUP BY b.bill_id, b.customer_id, b.amount, b.status;
```

### 4.2 Sample Test Queries
```sql
-- Test query: Find paid bills
SELECT * FROM bills WHERE status = 'PAID';

-- Test query: Find failed transactions
SELECT * FROM transactions WHERE status = 'FAILED';

-- Test query: Get transaction statistics
SELECT status, COUNT(*) as count, SUM(amount) as total_amount
FROM transactions
GROUP BY status;
```

## 5. Validation Checklist

- [ ] Docker & Docker Compose installed
- [ ] PostgreSQL container running (`docker-compose ps`)
- [ ] Database `payment_system` created automatically
- [ ] Schema DDL executed without errors (check logs)
- [ ] Sample data inserted successfully
- [ ] Can connect to database dari host machine (localhost:5432)
- [ ] Can connect ke container (`docker-compose exec postgres`)
- [ ] Basic queries return expected results
- [ ] Postman API tests configured

## 6. Common Issues & Solutions

### 6.1 Docker/Container Issues
```bash
# Check container status
docker-compose ps

# View container logs
docker-compose logs postgres

# Restart container
docker-compose restart postgres

# Rebuild container (jika ada perubahan)
docker-compose down
docker-compose up -d --build
```

### 6.2 Database Connection Issues
```bash
# Test connection dari host machine
psql -h localhost -p 5432 -U postgres -d payment_system

# Test connection dari dalam container
docker-compose exec postgres psql -U postgres -d payment_system

# Reset database
docker-compose down
docker volume rm training-postgres_data
docker-compose up -d
```

### 6.3 Schema/Data Issues
```sql
-- Connect ke container
docker-compose exec postgres psql -U postgres -d payment_system

-- Clean sample data
DELETE FROM audit_logs;
DELETE FROM transactions;
DELETE FROM bills;

-- Re-insert sample data (dari dalam container)
\i /docker-entrypoint-initdb.d/sample-data.sql
```

## 7. Next Steps

Setelah berhasil menyelesaikan Day 1:
1. Database siap untuk digunakan
2. Pahami struktur data untuk payment processing
3. Siapkan untuk Day 2 (JPos integration)
4. Review ISO-8583 message format concepts