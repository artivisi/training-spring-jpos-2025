package com.artivisi.atm.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.jpos.util.NameRegistrar;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/debug")
@Slf4j
public class DebugController {

    @GetMapping("/nameregistrar")
    public String getNameRegistrar() {
        log.info("Checking NameRegistrar contents");
        StringBuilder sb = new StringBuilder();
        sb.append("NameRegistrar Contents:\n\n");

        NameRegistrar.getAsMap().forEach((key, value) -> {
            sb.append("Key: ").append(key).append("\n");
            sb.append("  Class: ").append(value.getClass().getName()).append("\n");

            // Special handling for MUX
            if (value instanceof org.jpos.iso.MUX) {
                org.jpos.iso.MUX mux = (org.jpos.iso.MUX) value;
                sb.append("  Connected: ").append(mux.isConnected()).append("\n");
            }

            // Special handling for QMUX QBean
            if (value instanceof org.jpos.q2.iso.QMUX) {
                org.jpos.q2.iso.QMUX qmux = (org.jpos.q2.iso.QMUX) value;
                sb.append("  State: ").append(qmux.getState()).append("\n");
                sb.append("  Running: ").append(qmux.running()).append("\n");
            }

            sb.append("  ToString: ").append(value).append("\n\n");
        });

        return sb.toString();
    }

    @GetMapping("/sysprops")
    public Map<String, String> getSystemProperties() {
        return System.getProperties().entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith("jpos."))
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString()
                ));
    }
}
