package com.trading.fix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * FIX Gateway Application
 * Handles FIX protocol communication with trading clients
 */
@SpringBootApplication
@EnableAsync
public class FIXGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FIXGatewayApplication.class, args);
    }
}

// Made with Bob
