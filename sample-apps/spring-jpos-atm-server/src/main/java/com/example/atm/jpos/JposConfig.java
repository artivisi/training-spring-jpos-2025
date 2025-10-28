package com.example.atm.jpos;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jpos.q2.Q2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Slf4j
public class JposConfig {

    @Value("${jpos.deploy.dir:deploy}")
    private String deployDir;

    private Q2 q2;

    @PostConstruct
    public void startQ2() {
        log.info("Initializing Q2 with deploy directory: {}", deployDir);

        File deployDirectory = new File(deployDir);
        if (!deployDirectory.exists()) {
            deployDirectory.mkdirs();
            log.info("Created deploy directory: {}", deployDirectory.getAbsolutePath());
        }

        q2 = new Q2(deployDir);

        Thread.ofVirtual()
                .name("q2-main")
                .start(() -> {
                    try {
                        q2.start();
                        log.info("Q2 started successfully");
                    } catch (Exception e) {
                        log.error("Error starting Q2: ", e);
                    }
                });

        log.info("Q2 initialization completed");
    }

    @PreDestroy
    public void stopQ2() {
        log.info("Shutting down Q2");

        try {
            if (q2 != null) {
                q2.shutdown(true);
                log.info("Q2 stopped successfully");
            }
        } catch (Exception e) {
            log.error("Error during Q2 shutdown: ", e);
        }
    }
}
