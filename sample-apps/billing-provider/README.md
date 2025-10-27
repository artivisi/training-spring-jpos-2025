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