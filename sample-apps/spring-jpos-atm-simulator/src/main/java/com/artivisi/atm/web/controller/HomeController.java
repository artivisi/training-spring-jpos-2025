package com.artivisi.atm.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home controller for welcome page.
 * Displays application overview and navigation to main features.
 */
@Controller
@Slf4j
public class HomeController {

    /**
     * Display welcome/home page.
     */
    @GetMapping("/")
    public String home() {
        log.debug("Displaying welcome page");
        return "welcome";
    }
}
