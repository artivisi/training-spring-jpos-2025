package com.example.atm.config;

import com.example.atm.dto.hsm.PinFormat;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hsm")
public class HsmProperties {
    private String url;
    private Pin pin;
    private Connection connection;

    @Data
    public static class Pin {
        private EncryptedPinBlock encryptedPinBlock;
        private Pvv pvv;
        private String terminalId;
        private PinFormat format;

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
    public static class Connection {
        private int timeout;
        private int readTimeout;
    }
}
