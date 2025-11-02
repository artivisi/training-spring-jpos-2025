# Hari 5 – Connection Resiliency & Production Readiness

## Tujuan
- Persistent connection dengan heartbeat & automatic re-sign-on
- Server-initiated key rotation via admin REST API
- Key lifecycle management (PENDING → ACTIVE → EXPIRED)
- End-to-end testing seluruh sistem (transactions & key rotation)
- Monitoring dengan Prometheus & Grafana
- Best practices untuk deployment, security, dan performance

## 1. Persistent Connection & Automatic Re-Sign-On

### 1.1 Connection Lifecycle
```mermaid
graph TB
    subgraph "Strategi Retry"
        A[Request Failed] --> B{Transient Error?}
        B -->|Yes| C[Calculate Backoff]
        B -->|No| D[Return Error]

        C --> E{Max Retries Reached?}
        E -->|No| F[Wait Backoff Period]
        E -->|Yes| G[Mark as Failed]

        F --> H[Retry Request]
        H --> I{Request Success?}
        I -->|Yes| J[Return Success]
        I -->|No| B
    end

    style C fill:#e8f5e8
    style F fill:#fff3e0
    style H fill:#e1f5fe
```

### 1.2 Implementasi Exponential Backoff
**Peserta akan mengimplementasikan:**
- **Configurable retry attempts** (3-5 retries)
- **Exponential backoff** (1s, 2s, 4s, 8s, 16s)
- **Maximum timeout limits** (30-60 seconds)
- **Smart retry logic** (retry only transient failures)

### 1.3 Circuit Breaker Pattern
```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: Failure Threshold Reached
    OPEN --> HALF_OPEN: Timeout Period
    HALF_OPEN --> CLOSED: Success
    HALF_OPEN --> OPEN: Failure
    OPEN --> CLOSED: Reset Timeout

    note right of CLOSED
        Normal operation
        All requests pass through
        Track failure rate
    end note

    note right of OPEN
        Reject all requests
        No retry attempts
        Wait for recovery
    end note

    note right of HALF_OPEN
        Limited requests allowed
        Test system recovery
        Return to OPEN if fail
    end note
```

## 2. Store-and-Forward Pattern

### 2.1 Arsitektur Store-and-Forward
```mermaid
graph TB
    subgraph "Store-and-Forward System"
        A[Incoming Request] --> B[Validate Request]
        B --> C{Connection Available?}

        C -->|Yes| D[Process Immediately]
        C -->|No| E[Store in Queue]

        E --> F[Queue Manager]
        F --> G[Monitor Connection]
        G --> H{Connection Restored?}

        H -->|Yes| I[Process Queue FIFO]
        H -->|No| J[Keep in Queue]

        I --> K[Update Transaction Status]
        D --> K
    end

    style E fill:#f3e5f5
    style F fill:#e8f5e8
    style I fill:#e1f5fe
```

### 2.2 Implementasi Message Queue
**Peserta akan mengimplementasikan:**
- **Persistent queue** untuk transaksi gagal
- **FIFO processing** dengan penanganan prioritas
- **Duplicate detection** dan idempotency
- **Manual retry** dan cancellation capabilities

### 2.3 Schema Database Queue
Schema queue tersedia di: `production/store-forward-schema.sql`

### 2.4 Logika Pemrosesan Queue
```mermaid
sequenceDiagram
    participant Client
    participant Queue
    participant Processor
    participant Backend

    Client->>Queue: Store Request (Offline)
    Note over Client,Queue: Connection unavailable

    Queue->>Queue: Persist Request
    Queue->>Client: Acknowledge Storage

    Backend->>Queue: Connection Restored
    Queue->>Processor: Start Queue Processing
    Processor->>Backend: Process Stored Requests
    Backend->>Processor: Process Responses
    Processor->>Queue: Update Status
```

## 3. Peningkatan Manajemen Koneksi

### 3.1 Connection Pooling Lanjutan
```mermaid
graph TB
    subgraph "Connection Pool Management"
        A[Connection Pool] --> B[Primary Connections]
        A --> C[Backup Connections]
        A --> D[Health Monitor]

        B --> E[Load Balancer]
        C --> E
        D --> F[Connection Validator]
        F --> G[Reconnection Service]
        G --> H[Pool Manager]
    end

    subgraph "Health Checks"
        I[Heartbeat Service]
        J[Response Time Monitor]
        K[Error Rate Tracker]
        L[Capacity Monitor]
    end

    D --> I
    D --> J
    D --> K
    D --> L

    style A fill:#e1f5fe
    style D fill:#e8f5e8
    style H fill:#f3e5f5
```

### 3.2 Implementasi Health Monitoring
**Peserta akan mengimplementasikan:**
- **Connection health checks** (interval 30 detik)
- **Automatic reconnection** dengan exponential backoff
- **Connection status dashboard** dengan monitoring real-time
- **Performance metrics** tracking dan alerting

## 4. Setup Monitoring Produksi

### 4.1 Arsitektur Monitoring
```mermaid
graph TB
    subgraph "Application Services"
        A[Acquirer :8080]
        G[Gateway :8081]
        B[Billing :8082]
        H[HSM :8083]
    end

    subgraph "Monitoring Stack"
        P[Prometheus]
        G2[Grafana]
        A2[AlertManager]
        L[ Loki ]
    end

    subgraph "Metrics Collection"
        M1[Spring Actuator]
        M2[Custom Metrics]
        M3[JVM Metrics]
        M4[Business Metrics]
    end

    A --> M1
    G --> M2
    B --> M3
    H --> M4

    M1 --> P
    M2 --> P
    M3 --> P
    M4 --> P

    P --> G2
    P --> A2
    A --> L
    G --> L
    B --> L

    style P fill:#e65100
    style G2 fill:#1565c0
    style A2 fill:#c62828
    style L fill:#ff6f00
```

### 4.2 Konfigurasi Prometheus dengan jPOS 3

**jPOS 3 Built-in Prometheus Support:**

jPOS 3 menyediakan embedded Prometheus endpoint server yang dapat digunakan out of the box. jPOS 3 menggunakan **Micrometer** untuk instrumentasi metrics yang terintegrasi dengan Prometheus dan OpenTelemetry.

**Konfigurasi jPOS Q2 dengan Metrics:**
```bash
# Start Q2 with Prometheus metrics endpoint
java -jar jpos.jar --metrics-port=18583 --metrics-path=/metrics

# Or using q2 script
bin/q2 --metrics-port=18583 --metrics-path=/metrics
```

**Konfigurasi Prometheus:**
Konfigurasi Prometheus tersedia di: `production/prometheus.yml`

File konfigurasi akan men-scrape metrics dari:
- **jPOS metrics endpoint**: `http://localhost:18583/metrics`
- **Spring Boot Actuator** (Bank Server): `http://localhost:9090/actuator/prometheus`
- **HSM Simulator**: `http://localhost:8080/actuator/prometheus`

**Metrics jPOS yang tersedia:**
- ISO messages processed (total, success, failure)
- Transaction throughput (TPS - Transactions Per Second)
- MUX activity dan latency
- Active sessions dan active connections
- TransactionManager participants metrics
- Channel dan Server statistics
- JVM metrics (memory, GC, threads, class loading)

### 4.3 Konfigurasi Dashboard Grafana

**jPOS-Specific Metrics Dashboard:**

Grafana dashboard dikonfigurasi dengan dua kategori metrics:

1. **jPOS Application Metrics:**
   - ISO-8583 message processing rate
   - Transaction throughput (TPS)
   - MUX performance dan active sessions
   - Channel connections (active/idle)
   - TransactionManager response times (p50, p95, p99)
   - HSM operations (PIN verification, MAC generation)

2. **JVM Metrics:**
   - Heap memory usage dan GC statistics
   - Thread counts dan states
   - Class loading metrics
   - CPU usage

Konfigurasi dashboard Grafana tersedia di: `production/grafana-dashboard.json`

**Docker Compose Monitoring Stack:**
Konfigurasi lengkap tersedia di: `production/docker-compose.monitoring.yml`

Stack mencakup:
- **Prometheus** (port 9090) - Metrics collection
- **Grafana** (port 3000) - Visualization
- **Preconfigured dashboards** untuk jPOS dan Spring Boot

**Akses Monitoring:**
- Prometheus UI: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)
- jPOS Metrics Endpoint: `http://localhost:18583/metrics`

### 4.4 Metrik Kunci untuk Monitoring
**Application Metrics:**
- **Transaction throughput** (transaksi/detik)
- **Response time** (persentil p50, p95, p99)
- **Error rate** (4xx, 5xx, kode respons ISO-8583)
- **Connection pool status** (active, idle, exhausted)

**Business Metrics:**
- **Payment success rate** berdasarkan biller dan amount
- **Transaction value distribution**
- **Peak hour analysis**
- **Customer satisfaction metrics**

**Infrastructure Metrics:**
- **CPU/Memory/Disk** usage
- **Network latency** dan throughput
- **Database connection** pool status
- **JVM performance** metrics

## 5. Konfigurasi Deployment

### 5.1 Konfigurasi Docker untuk Produksi
Konfigurasi Docker produksi tersedia di: `production/docker-compose.prod.yml`

### 5.2 Konfigurasi Spesifik Environment
**Environment Variables Produksi:**
```yaml
# Database
spring.datasource.url=jdbc:postgresql://postgres:5432/payment_system
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}

# Connection Pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000

# Retry Configuration
retry.max-attempts=3
retry.backoff-delay=1000
retry.max-timeout=30000

# Store-and-Forward
queue.enabled=true
queue.max-size=10000
queue.retry-interval=5000

# Monitoring
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

### 5.3 Deployment Kubernetes
Konfigurasi Kubernetes tersedia di: `production/k8s-deployment.yaml`

## 6. Security Hardening

### 6.1 Checklist Kepatuhan PCI DSS
**Peserta akan mengimplementasikan:**
- **Secure transmission** untuk semua data in transit
- **Encryption at rest** untuk sensitive data
- **Access control** dengan principle of least privilege
- **Audit logging** untuk semua security events
- **Vulnerability scanning** dan patching

### 6.2 Konfigurasi Security Headers
```yaml
# Spring Security Headers
security.headers.frame-options=deny
security.headers.content-type-options=nosniff
security.headers.xss-protection=1; mode=block
security.headers.referrer-policy=strict-origin-when-cross-origin
```

### 6.3 Implementasi Rate Limiting
**Peserta akan mengimplementasikan:**
- **API rate limiting** (request per menit per client)
- **Connection rate limiting** (koneksi per IP)
- **Payment frequency limiting** (pembayaran per customer)
- **DDoS protection** dengan automatic blocking

## 7. Pengujian & Validasi

### 7.1 Skenario Pengujian End-to-End
Skenario pengujian end-to-end tersedia di: `production/e2e-test-scenarios.json`

### 7.2 Konfigurasi Load Testing
**Menggunakan JMeter/k6 untuk pengujian performa:**
```yaml
# Load Test Scenarios
load_test:
  users: 100
  duration: 10m
  ramp_up: 2m
  scenarios:
    - name: "Normal Payment Flow"
      weight: 70
    - name: "Retry Scenarios"
      weight: 20
    - name: "Error Handling"
      weight: 10
```

### 7.3 Chaos Engineering
**Skenario chaos yang akan diuji:**
- **Network partition** simulation
- **Database connection** failure
- **Service unavailability** testing
- **Resource exhaustion** scenarios

### 7.4 Checklist Validasi
- [ ] Semua mekanisme retry berfungsi dengan benar
- [ ] Pemrosesan store-and-forward queue berfungsi
- [ ] Circuit breaker pattern diimplementasikan
- [ ] Dashboard monitoring dikonfigurasi
- [ ] Alert dikonfigurasi dan diuji
- [ ] Load testing memenuhi target performa
- [ ] Chaos engineering tests lulus
- [ ] Security hardening lengkap
- [ ] Dokumentasi diperbarui
- [ ] Deployment scripts diuji

## 8. Prosedur Operasional

### 8.1 Template Runbook
**Prosedur operasional umum:**
- **Service restart** procedures
- **Database maintenance** procedures
- **Security incident** response
- **Performance troubleshooting** guide

### 8.2 Backup dan Recovery
**Peserta akan mengimplementasikan:**
- **Database backup** procedures (harian/mingguan)
- **Configuration backup** dan versioning
- **Disaster recovery** testing
- **RTO/RPO** documentation

### 8.3 Aturan Alert Monitoring
**Alert kritis:**
- **Service down** (> 1 menit)
- **High error rate** (> 5% selama 5 menit)
- **Slow response time** (> 2 detik p95)
- **Database connection** issues
- **Queue backlog** (> 1000 transaksi)

## 9. Penilaian Kesiapan Produksi

### 9.1 Checklist Kesiapan
**Kesiapan Teknis:**
- [ ] Semua services dikontainerisasi dan diuji
- [ ] Infrastructure as code diimplementasikan
- [ ] Monitoring dan alerting dikonfigurasi
- [ ] Prosedur backup didokumentasikan
- [ ] Security scan selesai
- [ ] Target performa tercapai

**Kesiapan Operasional:**
- [ ] Runbooks dibuat dan diuji
- [ ] Training tim selesai
- [ ] Prosedur support didefinisikan
- [ ] Jalur eskalasi diidentifikasi
- [ ] Rencana komunikasi disiapkan

**Kesiapan Bisnis:**
- [ ] Perjanjian SLA didefinisikan
- [ ] Perencanaan kapasitas selesai
- [ ] Business continuity diuji
- [ ] Training pengguna selesai
- [ ] Rencana go-live disetujui

### 9.2 Proses Go-Live
```mermaid
graph TB
    A[Pre-Live Checklist] --> B[Infrastructure Validation]
    B --> C[Service Deployment]
    C --> D[Smoke Tests]
    D --> E[Monitoring Verification]
    E --> F[Performance Validation]
    F --> G[Business Validation]
    G --> H[Go-Live Decision]
    H --> I[Live Monitoring]
    I --> J[Post-Live Review]

    style H fill:#4caf50
    style I fill:#2196f3
    style J fill:#ff9800
```

## 10. Ringkasan Best Practices

### 10.1 Best Practices Pengembangan
- **Code quality** dengan automated testing
- **Security by design** principles
- **Performance optimization** dari awal
- **Observability** built-in
- **Documentation** as code

### 10.2 Best Practices Operasional
- **Infrastructure as code** untuk konsistensi
- **Automated deployment** pipelines
- **Proactive monitoring** dan alerting
- **Regular security** assessments
- **Continuous improvement** process

## 11. Next Steps

Setelah berhasil menyelesaikan Day 5:
1. Persistent connection dengan heartbeat implemented
2. Automatic re-sign-on berfungsi
3. Server-initiated key rotation via REST API berfungsi
4. Key lifecycle management (PENDING → ACTIVE → EXPIRED) implemented
5. Automatic channel deregistration on disconnect
6. Transaction manager refactoring dengan group-based routing
7. Monitoring dan logging comprehensive
8. Sistem ATM production-ready

## 12. Final Project Deliverables

**Sistem ATM production-ready lengkap dengan:**

**Day 1-3 Core Features:**
- ✅ Bank Server dengan jPOS Q2 Server (port 22222)
- ✅ ATM Simulator dengan Web UI (port 7070)
- ✅ PostgreSQL database untuk accounts & transactions
- ✅ Balance Inquiry (Processing Code 310000)
- ✅ Cash Withdrawal (Processing Code 010000)
- ✅ Transaction Participants dengan Spring Boot integration
- ✅ Automatic sign-on/sign-off

**Day 4 Security Features:**
- ✅ HSM simulator (port 8080)
- ✅ AES-128 PIN encryption (field 123)
- ✅ AES-CMAC message integrity (field 64)
- ✅ PIN verification
- ✅ Terminal-initiated key rotation (TPK/TSK)
- ✅ Crypto keys database management

**Day 5 Production Features:**
- ✅ Persistent connection dengan heartbeat
- ✅ Automatic re-sign-on capability
- ✅ Server-initiated key rotation via admin API
- ✅ Channel deregistration on disconnect
- ✅ Key lifecycle management
- ✅ Comprehensive logging
- ✅ Production-ready configuration