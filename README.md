# Training Spring JPos 2025 #

Technology Stack :

* Java 25
* Maven 3
* PostgreSQL 17
* Spring Boot 3.5.6
* JPos 3.0.0

## Materi Training ##

Hari 1 – Spring Boot Application Setup, REST, dan Database
* Pengenalan ekosistem pembayaran & ISO-8583
* Dasar Spring Boot & Dependency Injection
* Implementasi REST API (Acquirer & Billing Provider)
* Integrasi database PostgreSQL & logging transaksi

Hari 2 – JPOS Integration & ISO-8583 Basics
* Struktur ISO-8583 (MTI, Bitmap, Data Elements)
* Pesan administratif (Logon, Logoff, Echo)
* Pengenalan JPOS: Q2, Channels, MUX, Packager
* Praktikum: komunikasi ISO-8583 awal (Logon/Echo)

Hari 3 – End-to-End Bill Payment Flow
* Integrasi JPOS dengan Spring Boot
* Translasi JSON ↔ ISO-8583
* Implementasi alur pembayaran end-to-end
* Collaborative testing & debugging

Hari 4 – HSM Simulation, PIN, MAC & Key Exchange
* Konsep HSM, MAC, dan PIN
* Simulasi key exchange via ISO-8583
* Generasi & verifikasi MAC
* Pin Management, Pinblock, Pin Storage, Pin Verification
* Integrasi HSM ke alur pembayaran

Hari 5 – Connection Resiliency & Production Readiness
* Retry mechanism & reconnection
* Store-and-forward pattern
* End-to-end testing seluruh sistem
* Best practices (security, PCI DSS, deployment, monitoring Grafana)
* Diskusi & Q&A