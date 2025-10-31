package com.example.atm.jpos.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jpos.q2.Q2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@Slf4j
public class JposConfig {

    @Value("${jpos.mux.name:atm-mux}")
    private String muxName;

    @Value("${jpos.channel.host:localhost}")
    private String channelHost;

    @Value("${jpos.channel.port:22222}")
    private Integer channelPort;

    @Value("${jpos.channel.timeout:30000}")
    private Integer channelTimeout;

    @Value("${jpos.channel.class:org.jpos.iso.channel.ASCIIChannel}")
    private String channelClass;

    @Value("${jpos.packager.class:org.jpos.iso.packager.BASE24Packager}")
    private String packagerClass;

    private Q2 q2;
    private Thread q2Thread;

    @PostConstruct
    public void startQ2() throws IOException {
        log.info("Initializing Q2 container for ATM simulator");
        log.info("MUX name: {}", muxName);
        log.info("Channel: {} ({}:{})", channelClass, channelHost, channelPort);
        log.info("Packager: {}", packagerClass);

        // Set system properties for Q2 XML property placeholders
        System.setProperty("jpos.mux.name", muxName);
        System.setProperty("jpos.channel.host", channelHost);
        System.setProperty("jpos.channel.port", channelPort.toString());
        System.setProperty("jpos.channel.timeout", channelTimeout.toString());
        System.setProperty("jpos.channel.class", channelClass);
        System.setProperty("jpos.packager.class", packagerClass);

        // Determine deploy directory using three-tier approach
        File deployDirectory = determineDeployDirectory();

        q2 = new Q2(deployDirectory.getAbsolutePath());

        // Start Q2 in a virtual thread
        Thread.ofVirtual()
                .name("q2-main")
                .start(() -> {
                    try {
                        q2.start();
                        log.info("Q2 started successfully");
                    } catch (Exception e) {
                        log.error("Error starting Q2", e);
                    }
                });

        log.info("Q2 initialization completed");
    }

    @PreDestroy
    public void stopQ2() {
        log.info("Stopping Q2 container");
        if (q2 != null) {
            q2.stop();
            if (q2Thread != null) {
                try {
                    q2Thread.join(5000);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for Q2 thread to stop", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.info("Q2 container stopped");
    }

    /**
     * Prepares the deploy directory for Q2.
     * - In development (mvn spring-boot:run or IDE), uses target/classes/deploy or src/main/resources/deploy
     * - In packaged JAR, extracts deploy files to a temporary directory
     */
    private File determineDeployDirectory() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // Tier 1: Check if deploy directory exists in target/classes (Maven build)
        File targetClassesDeployDir = new File("target/classes/deploy");
        if (targetClassesDeployDir.exists() && targetClassesDeployDir.isDirectory()) {
            log.info("Using deploy directory from target/classes: {}", targetClassesDeployDir.getAbsolutePath());
            return targetClassesDeployDir;
        }

        // Tier 2: Check if running from src/main/resources (IDE or mvn spring-boot:run)
        File srcResourcesDeployDir = new File("src/main/resources/deploy");
        if (srcResourcesDeployDir.exists() && srcResourcesDeployDir.isDirectory()) {
            log.info("Using deploy directory from src/main/resources: {}", srcResourcesDeployDir.getAbsolutePath());
            return srcResourcesDeployDir;
        }

        // Tier 3: Running from JAR - extract deploy files to temporary directory
        log.info("Running from JAR - extracting deploy files from classpath");
        Path tempDeployDir = Files.createTempDirectory("jpos-deploy-");
        tempDeployDir.toFile().deleteOnExit();

        Resource[] resources = resolver.getResources("classpath:deploy/*.xml");
        if (resources.length == 0) {
            throw new IllegalStateException("No deploy XML files found in classpath:deploy");
        }

        log.info("Found {} deploy XML files to extract", resources.length);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename != null) {
                Path targetFile = tempDeployDir.resolve(filename);
                try (InputStream is = resource.getInputStream()) {
                    Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    log.debug("Extracted: {}", filename);
                }
            }
        }

        log.info("Deploy files extracted to: {}", tempDeployDir.toAbsolutePath());
        return tempDeployDir.toFile();
    }

    @Bean
    public String muxName() {
        // jPOS QMUX automatically prepends "mux." to the name attribute
        return "mux." + muxName;
    }
}
