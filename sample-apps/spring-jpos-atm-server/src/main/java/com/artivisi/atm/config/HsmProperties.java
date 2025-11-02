package com.artivisi.atm.config;

import com.artivisi.atm.dto.hsm.PinFormat;
import com.artivisi.atm.entity.PinEncryptionAlgorithm;
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
    private Keys keys;

    @Data
    public static class Pin {
        private EncryptedPinBlock encryptedPinBlock;
        private Pvv pvv;
        private PinFormat format;
        private PinEncryptionAlgorithm encryptionAlgorithm = PinEncryptionAlgorithm.AES_256;

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

    @Data
    public static class Keys {
        private String bankUuid;
        private String tskMasterKey;
        private String tpkMasterKey;
    }
}
