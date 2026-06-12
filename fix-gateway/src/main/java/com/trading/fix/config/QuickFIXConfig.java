package com.trading.fix.config;

import com.trading.fix.handler.TradingApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import java.io.InputStream;

/**
 * QuickFIX/J Configuration
 */
@Slf4j
@Configuration
public class QuickFIXConfig {

    @Bean
    public SessionSettings sessionSettings() throws ConfigError {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("quickfix.cfg")) {
            if (inputStream == null) {
                throw new ConfigError("quickfix.cfg not found in classpath");
            }
            return new SessionSettings(inputStream);
        } catch (Exception e) {
            throw new ConfigError("Failed to load QuickFIX settings", e);
        }
    }

    @Bean
    public MessageStoreFactory messageStoreFactory(SessionSettings settings) {
        return new FileStoreFactory(settings);
    }

    @Bean
    public LogFactory logFactory(SessionSettings settings) {
        return new FileLogFactory(settings);
    }

    @Bean
    public MessageFactory messageFactory() {
        return new DefaultMessageFactory();
    }

    @Bean
    public Acceptor acceptor(
            TradingApplication application,
            MessageStoreFactory storeFactory,
            SessionSettings settings,
            LogFactory logFactory,
            MessageFactory messageFactory) throws ConfigError {
        
        return new SocketAcceptor(
                application,
                storeFactory,
                settings,
                logFactory,
                messageFactory
        );
    }

    @Bean
    public FIXGatewayLifecycle fixGatewayLifecycle(Acceptor acceptor) {
        return new FIXGatewayLifecycle(acceptor);
    }

    /**
     * Manages FIX Gateway lifecycle
     */
    @Slf4j
    public static class FIXGatewayLifecycle {
        private final Acceptor acceptor;

        public FIXGatewayLifecycle(Acceptor acceptor) {
            this.acceptor = acceptor;
        }

        @jakarta.annotation.PostConstruct
        public void start() {
            try {
                log.info("Starting FIX Gateway...");
                acceptor.start();
                log.info("FIX Gateway started successfully");
            } catch (Exception e) {
                log.error("Failed to start FIX Gateway", e);
                throw new RuntimeException("Failed to start FIX Gateway", e);
            }
        }

        @jakarta.annotation.PreDestroy
        public void stop() {
            try {
                log.info("Stopping FIX Gateway...");
                acceptor.stop();
                log.info("FIX Gateway stopped successfully");
            } catch (Exception e) {
                log.error("Error stopping FIX Gateway", e);
            }
        }
    }
}

// Made with Bob
