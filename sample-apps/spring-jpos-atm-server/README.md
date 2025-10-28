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
