# Tutorial JPos #

Membuat aplikasi dari [tutorial resmi JPos](https://github.com/jpos/tutorial)

## Langkah Setup ##

### Dengan Gradle ###

1. Buka repository tutorial : https://github.com/jpos/tutorial
2. Download zip
3. Extract
4. Jalankan perintah `gradle installApp`
5. Run aplikasi, ada 2 cara :
   - `gradle run`
   - `./bin/q2`

**Note** : pastikan Java versi 25 dan Gradle versi 9

### Dengan Maven ###

1. Build project: `mvn clean package`
2. Run aplikasi (pilih salah satu cara):

   **Cara 1: Menggunakan Maven exec**

   Linux/Mac:
   ```bash
   mvn package exec:exec
   ```

   Windows:
   ```cmd
   mvn package exec:exec -Dexec.executable="target\jpos-tutorial-0.0.0-SNAPSHOT\bin\q2.bat"
   ```

   **Cara 2: Menjalankan script langsung (Recommended)**

   Linux/Mac:
   ```bash
   ./target/jpos-tutorial-0.0.0-SNAPSHOT/bin/q2
   ```

   Windows:
   ```cmd
   target\jpos-tutorial-0.0.0-SNAPSHOT\bin\q2.bat
   ```

   **Cara 3: Build dan run dalam satu command**

   Linux/Mac:
   ```bash
   mvn clean package && ./target/jpos-tutorial-0.0.0-SNAPSHOT/bin/q2
   ```

   Windows:
   ```cmd
   mvn clean package && target\jpos-tutorial-0.0.0-SNAPSHOT\bin\q2.bat
   ```

**Note** : pastikan Java versi 25 dan Maven versi 3.9+

Distribution lengkap tersedia di: `target/jpos-tutorial-0.0.0-SNAPSHOT/` yang berisi:
- `bin/` - Startup scripts (q2, start, stop)
- `cfg/` - Configuration files
- `deploy/` - Q2 deployment descriptors
- `lib/` - All dependencies
- `log/` - Log directory
