# Spring Boot ATM Server

Spring Boot application for bank operations with PostgreSQL database.

## Features

- Balance inquiry
- Cash withdrawal
- Transaction history tracking
- PostgreSQL 17 database
- Flyway database migrations
- Virtual threads support (Java 25)

## Prerequisites

- Java 25
- Docker and Docker Compose
- Maven

## Database Setup

Start PostgreSQL using Docker Compose:

```bash
docker-compose up -d
```

This will start PostgreSQL 17 on port 5432 with the following credentials:
- Database: `bankdb`
- Username: `bankuser`
- Password: `bankpass`

## Running the Application

```bash
mvn spring-boot:run
```

The application will:
1. Connect to PostgreSQL
2. Run Flyway migrations to create tables
3. Insert sample data
4. Start on default port 8080

## Database Schema

### Accounts Table
- `id`: Primary key
- `account_number`: Unique account identifier
- `account_holder_name`: Customer name
- `balance`: Current balance
- `currency`: Currency code (default: IDR)
- `account_type`: SAVINGS, CHECKING, CREDIT
- `status`: ACTIVE, INACTIVE, BLOCKED, CLOSED
- `version`: Optimistic locking version

### Transactions Table
- `id`: Primary key
- `account_id`: Foreign key to accounts
- `transaction_type`: BALANCE_INQUIRY, WITHDRAWAL, DEPOSIT, etc.
- `amount`: Transaction amount
- `balance_before`: Balance before transaction
- `balance_after`: Balance after transaction
- `description`: Transaction description
- `reference_number`: Unique transaction reference
- `transaction_date`: When transaction occurred

## Sample Data

Three test accounts are created:
- Account: `1234567890` - John Doe (Balance: 5,000,000 IDR)
- Account: `0987654321` - Jane Smith (Balance: 3,000,000 IDR)
- Account: `5555555555` - Bob Johnson (Balance: 10,000,000 IDR)

## REST API Endpoints

Base URL: `http://localhost:8080/api/bank`

### 1. Balance Inquiry

**Endpoint:** `POST /api/bank/balance-inquiry`

**Description:** Query account balance without modifying data (read-only operation)

**Request Body:**
```json
{
  "accountNumber": "1234567890"
}
```

**Success Response (200 OK):**
```json
{
  "accountNumber": "1234567890",
  "accountHolderName": "John Doe",
  "balance": 5000000.00,
  "currency": "IDR",
  "accountType": "SAVINGS",
  "timestamp": "2025-10-28T10:30:00",
  "referenceNumber": "A1B2C3D4E5F6G7H8"
}
```

**Error Responses:**
- `404 NOT FOUND` - Account not found
```json
{
  "timestamp": "2025-10-28T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found: 1234567890",
  "path": "/api/bank/balance-inquiry"
}
```

- `403 FORBIDDEN` - Account not active
```json
{
  "timestamp": "2025-10-28T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Account is not active: 1234567890",
  "path": "/api/bank/balance-inquiry"
}
```

- `400 BAD REQUEST` - Validation error
```json
{
  "timestamp": "2025-10-28T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/bank/balance-inquiry",
  "validationErrors": {
    "accountNumber": "Account number is required"
  }
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/bank/balance-inquiry \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"1234567890"}'
```

### 2. Cash Withdrawal

**Endpoint:** `POST /api/bank/withdrawal`

**Description:** Withdraw cash from account and record transaction

**Request Body:**
```json
{
  "accountNumber": "1234567890",
  "amount": 500000.00
}
```

**Success Response (201 CREATED):**
```json
{
  "accountNumber": "1234567890",
  "accountHolderName": "John Doe",
  "withdrawalAmount": 500000.00,
  "balanceBefore": 5000000.00,
  "balanceAfter": 4500000.00,
  "currency": "IDR",
  "timestamp": "2025-10-28T10:30:00",
  "referenceNumber": "B2C3D4E5F6G7H8I9"
}
```

**Error Responses:**
- `404 NOT FOUND` - Account not found
- `403 FORBIDDEN` - Account not active
- `400 BAD REQUEST` - Insufficient balance
```json
{
  "timestamp": "2025-10-28T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient balance. Current balance: 5000000.00, requested: 6000000.00",
  "path": "/api/bank/withdrawal"
}
```

- `400 BAD REQUEST` - Validation errors
```json
{
  "timestamp": "2025-10-28T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/bank/withdrawal",
  "validationErrors": {
    "accountNumber": "Account number is required",
    "amount": "Amount must be positive"
  }
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/bank/withdrawal \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"1234567890","amount":500000.00}'
```

## Configuration

Virtual threads are enabled by default in `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

## Tech Stack

- Spring Boot 4.0.0-RC1
- Java 25
- PostgreSQL 17
- Flyway DB
- Spring Data JPA
- Hibernate
- Lombok
