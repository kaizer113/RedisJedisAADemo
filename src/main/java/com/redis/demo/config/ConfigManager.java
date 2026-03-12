package com.redis.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Configuration manager for loading and accessing application properties.
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "redis.properties";
    
    private final Properties properties;
    
    public ConfigManager() {
        this.properties = new Properties();
        loadProperties();
    }
    
    private void loadProperties() {
        // Try to load from external file first (no recompile needed)
        java.io.File externalFile = new java.io.File(CONFIG_FILE);

        try {
            if (externalFile.exists()) {
                // Load from external file in current directory
                try (InputStream input = new java.io.FileInputStream(externalFile)) {
                    properties.load(input);
                    logger.info("✅ Configuration loaded from EXTERNAL file: {}", externalFile.getAbsolutePath());
                    logger.info("   (Changes to this file take effect immediately - no rebuild needed!)");
                }
            } else {
                // Fallback to classpath (packaged in JAR)
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (input == null) {
                        logger.error("Unable to find {}", CONFIG_FILE);
                        throw new RuntimeException("Configuration file not found: " + CONFIG_FILE);
                    }
                    properties.load(input);
                    logger.info("Configuration loaded from classpath (packaged in JAR)");
                    logger.info("💡 Tip: Copy redis.properties to current directory to edit without rebuilding");
                }
            }
        } catch (IOException e) {
            logger.error("Error loading configuration file", e);
            throw new RuntimeException("Failed to load configuration", e);
        }
    }
    
    public List<String> getRedisEndpoints() {
        String eastEndpoint = properties.getProperty("redis.endpoint.east");
        String westEndpoint = properties.getProperty("redis.endpoint.west");

        if (eastEndpoint == null || eastEndpoint.trim().isEmpty()) {
            throw new RuntimeException("redis.endpoint.east must be configured in redis.properties");
        }
        if (westEndpoint == null || westEndpoint.trim().isEmpty()) {
            throw new RuntimeException("redis.endpoint.west must be configured in redis.properties");
        }

        return Arrays.asList(eastEndpoint, westEndpoint);
    }
    
    public long getWriterIntervalMs() {
        return Long.parseLong(properties.getProperty("writer.interval.ms", "1000"));
    }
    
    public int getValueSize() {
        return Integer.parseInt(properties.getProperty("value.size", "500"));
    }

    public int getMetricsIntervalSeconds() {
        return Integer.parseInt(properties.getProperty("metrics.interval.seconds", "10"));
    }

    public boolean isBackgroundLoadEnabled() {
        return Boolean.parseBoolean(properties.getProperty("background.load.enabled", "true"));
    }

    public int getBackgroundLoadThreads() {
        return Integer.parseInt(properties.getProperty("background.load.threads", "10"));
    }

    public int getBackgroundLoadReadWriteRatio() {
        return Integer.parseInt(properties.getProperty("background.load.read.write.ratio", "10"));
    }

    public int getBackgroundLoadSleepNanos() {
        return Integer.parseInt(properties.getProperty("background.load.sleep.nanos", "100000"));
    }
    
    public int getKeyTtlSeconds() {
        return Integer.parseInt(properties.getProperty("key.ttl.seconds", "300"));
    }
    
    public int getPoolMaxTotal() {
        return Integer.parseInt(properties.getProperty("redis.pool.max.total", "50"));
    }
    
    public int getPoolMaxIdle() {
        return Integer.parseInt(properties.getProperty("redis.pool.max.idle", "20"));
    }
    
    public int getPoolMinIdle() {
        return Integer.parseInt(properties.getProperty("redis.pool.min.idle", "5"));
    }

    // ================================================================================
    // Failover Configuration Getters
    // ================================================================================

    // Circuit Breaker Configuration
    public int getCircuitBreakerSlidingWindowSize() {
        return Integer.parseInt(properties.getProperty("failover.circuit.breaker.sliding.window.size", "2"));
    }

    public int getCircuitBreakerMinNumFailures() {
        return Integer.parseInt(properties.getProperty("failover.circuit.breaker.min.num.failures", "1000"));
    }

    public float getCircuitBreakerFailureRateThreshold() {
        return Float.parseFloat(properties.getProperty("failover.circuit.breaker.failure.rate.threshold", "10.0"));
    }

    // Retry Configuration
    public int getRetryMaxAttempts() {
        return Integer.parseInt(properties.getProperty("failover.retry.max.attempts", "3"));
    }

    public int getRetryWaitDuration() {
        return Integer.parseInt(properties.getProperty("failover.retry.wait.duration", "500"));
    }

    public int getRetryExponentialBackoffMultiplier() {
        return Integer.parseInt(properties.getProperty("failover.retry.exponential.backoff.multiplier", "2"));
    }

    // General Failover Configuration
    public int getMaxNumFailoverAttempts() {
        return Integer.parseInt(properties.getProperty("failover.max.num.failover.attempts", "10"));
    }

    public int getDelayBetweenFailoverAttempts() {
        return Integer.parseInt(properties.getProperty("failover.delay.between.attempts", "12000"));
    }

    public int getGracePeriod() {
        return Integer.parseInt(properties.getProperty("failover.grace.period", "60000"));
    }

    public boolean isFastFailover() {
        return Boolean.parseBoolean(properties.getProperty("failover.fast.failover", "false"));
    }

    public boolean isRetryOnFailover() {
        return Boolean.parseBoolean(properties.getProperty("failover.retry.on.failover", "false"));
    }

    // Failback Configuration
    public boolean isFailbackSupported() {
        return Boolean.parseBoolean(properties.getProperty("failover.failback.supported", "true"));
    }

    public int getFailbackCheckInterval() {
        return Integer.parseInt(properties.getProperty("failover.failback.check.interval", "120000"));
    }

    // Health Check Configuration
    public int getHealthCheckInterval() {
        return Integer.parseInt(properties.getProperty("failover.health.check.interval", "1000"));
    }

    public int getHealthCheckTimeout() {
        return Integer.parseInt(properties.getProperty("failover.health.check.timeout", "1000"));
    }

    public int getHealthCheckNumProbes() {
        return Integer.parseInt(properties.getProperty("failover.health.check.num.probes", "3"));
    }

    public int getHealthCheckDelayBetweenProbes() {
        return Integer.parseInt(properties.getProperty("failover.health.check.delay.between.probes", "100"));
    }
}

