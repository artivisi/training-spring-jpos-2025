# Aplikasi Billing Provider #

Database development dengan Podman Compose

1. Menjalankan database development

    ```
    podman compose up
    ```

2. Stop database development

    ```
    Ctrl-C
    podman compose down
    ```

3. Hapus database development

    ```
    rm -rf db-billing
    ```

    Atau langsung hapus saja folder `db-billing`

Menjalankan Aplikasi Spring Boot

1. Clean compile dan run

    ```
    mvn clean spring-boot:run
    ```

2. Stop aplikasi

    ```
    Ctrl-C
    ```

3. Mengakses REST API untuk mengecek tagihan : `http://localhost:8080/api/billings/?productCode=PLN&customerNumber=123456789`

    Outputnya seperti ini

    ```json
    [{"id":"e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b","product":{"id":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","code":"PLN","name":"Listrik PLN"},"billingPeriod":"2025-10-01","customerNumber":"123456789","amount":450000.00,"paid":false,"description":"Tagihan Listrik Oktober 2025"},{"id":"f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b0c","product":{"id":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","code":"PLN","name":"Listrik PLN"},"billingPeriod":"2025-09-01","customerNumber":"123456789","amount":425000.00,"paid":false,"description":"Tagihan Listrik September 2025"},{"id":"a7b8c9d0-e1f2-4a3b-4c5d-6e7f8a9b0c1d","product":{"id":"a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d","code":"PLN","name":"Listrik PLN"},"billingPeriod":"2025-11-01","customerNumber":"123456789","amount":475000.00,"paid":false,"description":"Tagihan Listrik November 2025"}]
    ```

4. Melakukan payment, lakukan request `POST` ke `http://localhost:8080/api/billings/{billing-id}/payments`. Misalnya : `http://localhost:8080/api/billings/e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b/payments`

    Outputnya seperti ini

    ```json
    {
        "id": "889d02bc-2e5f-4d9f-8e6a-3babe9ba5b23",
        "billing": {
            "id": "e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b",
            "product": {
                "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
                "code": "PLN",
                "name": "Listrik PLN"
            },
            "billingPeriod": "2025-10-01",
            "customerNumber": "123456789",
            "amount": 450000.00,
            "paid": true,
            "description": "Tagihan Listrik Oktober 2025"
        },
        "amount": 450000.00,
        "paymentReferences": "cb51b477-0a76-4b6b-86a8-224cf14492fd",
        "transactionTime": "2025-10-27T14:44:23.273059"
    }
    ```