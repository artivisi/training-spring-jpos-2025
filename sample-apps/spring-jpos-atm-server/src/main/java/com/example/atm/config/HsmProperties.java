package com.example.atm.config;

import com.example.atm.dto.hsm.PinFormat;
import com.example.atm.entity.PinEncryptionAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hsm")
public class HsmProperties {
    private String url;
    private Pin pin;
    private Mac mac;
    private Connection connection;

    @Data
    public static class Pin {
        private EncryptedPinBlock encryptedPinBlock;
        private Pvv pvv;
        private String terminalId;
        private PinFormat format;
        private PinEncryptionAlgorithm encryptionAlgorithm = PinEncryptionAlgorithm.TDES;

        @Data
        public static class EncryptedPinBlock {
            private String endpoint;
        }

        @Data
        public static class Pvv {
            private String endpoint;
        }
    }

    @Data
    public static class Mac {
        private MacAlgorithm algorithm = MacAlgorithm.AES_CMAC;
        private boolean verifyEnabled = true;
        private boolean generateEnabled = true;
    }

    public enum MacAlgorithm {
        AES_CMAC,
        HMAC_SHA256_TRUNCATED
    }

    @Data
    public static class Connection {
        private int timeout;
        private int readTimeout;
    }
}
