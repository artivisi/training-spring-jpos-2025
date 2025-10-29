package com.example.atm.config;

import com.example.atm.service.HsmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class HsmConfig {

    private final HsmProperties hsmProperties;

    @Bean
    public RestClient hsmRestClient() {
        String baseUrl = hsmProperties.getUrl();

        log.info("Configuring HSM RestClient with base URL: {}", baseUrl);
        log.info("  Encrypted PIN Block endpoint: {}", hsmProperties.getPin().getEncryptedPinBlock().getEndpoint());
        log.info("  PVV endpoint: {}", hsmProperties.getPin().getPvv().getEndpoint());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    log.debug("HSM Request: {} {}", request.getMethod(), request.getURI());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public HsmClient hsmClient(RestClient hsmRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(hsmRestClient))
                .build();

        return factory.createClient(HsmClient.class);
    }
}
