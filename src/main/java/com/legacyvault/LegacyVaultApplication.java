package com.legacyvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the LegacyVault Spring Boot application.
 */
@SpringBootApplication
@EnableScheduling
public class LegacyVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyVaultApplication.class, args);
    }
}
