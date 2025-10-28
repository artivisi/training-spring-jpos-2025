package com.example.atm.jpos;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jpos.q2.Q2;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${jpos.deploy.dir:deploy}")
    private String deployDir;

    @Value("${jpos.server.port:22222}")
    private int serverPort;

    @Value("${jpos.server.max-sessions:100}")
    private int maxSessions;

    @Value("${jpos.server.channel:org.jpos.iso.channel.ASCIIChannel}")
    private String channel;

    @Value("${jpos.server.packager:org.jpos.iso.packager.BASE24Packager}")
    private String packager;

    private Q2 q2;

    @PostConstruct
    public void startQ2() throws IOException {
        log.info("Initializing Q2 with deploy directory: {}", deployDir);
        log.info("jPOS server port: {}", serverPort);
        log.info("jPOS max sessions: {}", maxSessions);
        log.info("jPOS channel: {}", channel);
        log.info("jPOS packager: {}", packager);

        // Set system properties for Q2 XML property placeholders
        System.setProperty("jpos.server.port", String.valueOf(serverPort));
        System.setProperty("jpos.server.max-sessions", String.valueOf(maxSessions));
        System.setProperty("jpos.server.channel", channel);
        System.setProperty("jpos.server.packager", packager);

        // Prepare deploy directory - extract from classpath if needed
        File deployDirectory = prepareDeployDirectory();

        q2 = new Q2(deployDirectory.getAbsolutePath());

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

    /**
     * Prepares the deploy directory for Q2.
     * - In development (mvn spring-boot:run or IDE), uses target/classes/deploy directly
     * - In packaged JAR, extracts deploy files to a temporary directory
     */
    private File prepareDeployDirectory() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // First, check if deploy directory exists in file system (development mode)
        File targetClassesDeployDir = new File("target/classes/" + deployDir);
        if (targetClassesDeployDir.exists() && targetClassesDeployDir.isDirectory()) {
            log.info("Using deploy directory from target/classes: {}", targetClassesDeployDir.getAbsolutePath());
            return targetClassesDeployDir;
        }

        // Check if running from src/main/resources (IDE or mvn spring-boot:run)
        File srcResourcesDeployDir = new File("src/main/resources/" + deployDir);
        if (srcResourcesDeployDir.exists() && srcResourcesDeployDir.isDirectory()) {
            log.info("Using deploy directory from src/main/resources: {}", srcResourcesDeployDir.getAbsolutePath());
            return srcResourcesDeployDir;
        }

        // Running from JAR - extract deploy files to temporary directory
        log.info("Running from JAR - extracting deploy files from classpath");
        Path tempDeployDir = Files.createTempDirectory("jpos-deploy-");
        tempDeployDir.toFile().deleteOnExit();

        Resource[] resources = resolver.getResources("classpath:" + deployDir + "/*.xml");
        if (resources.length == 0) {
            throw new IllegalStateException("No deploy XML files found in classpath:" + deployDir);
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
