package com.artivisi.atm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.artivisi.atm.service.HsmClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class HsmConfig {

    private final HsmProperties hsmProperties;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClient hsmRestClient(ObjectMapper objectMapper) {
        String baseUrl = hsmProperties.getUrl();

        log.info("Configuring HSM RestClient with base URL: {}", baseUrl);
        log.info("  Encrypted PIN Block endpoint: {}", hsmProperties.getPin().getEncryptedPinBlock().getEndpoint());
        log.info("  PVV endpoint: {}", hsmProperties.getPin().getPvv().getEndpoint());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    // Log request
                    log.info("→ HSM Request: {} {}", request.getMethod(), request.getURI());
                    if (body != null && body.length > 0) {
                        log.info("  Body: {}", new String(body));
                    }

                    // Execute and log response
                    var response = execution.execute(request, body);
                    log.info("← HSM Response: {}", response.getStatusCode());

                    return response;
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
