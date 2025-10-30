package com.example.atm.jpos.config;

import lombok.extern.slf4j.Slf4j;
import org.jpos.q2.Q2;
import org.jpos.util.NameRegistrar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@Slf4j
public class JposConfiguration {

    @Value("${jpos.q2.deployDir:deploy}")
    private String deployDir;

    @Value("${jpos.mux.name:atm-mux}")
    private String muxName;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Q2 q2Container() {
        log.info("Initializing Q2 container with deploy directory: {}", deployDir);

        File deployDirectory = new File(deployDir);
        if (!deployDirectory.exists()) {
            log.warn("Deploy directory does not exist, creating: {}", deployDir);
            deployDirectory.mkdirs();
        }

        Q2 q2 = new Q2(deployDir);
        log.info("Q2 container initialized successfully");

        return q2;
    }

    @Bean
    public String muxName() {
        return muxName;
    }
}
