package com.trading.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Matching Engine Application
 * High-performance order matching with price-time priority
 */
@SpringBootApplication
@EnableAsync
public class MatchingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineApplication.class, args);
    }
}

// Made with Bob
