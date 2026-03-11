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
        String endpoints = properties.getProperty("redis.endpoints", "localhost:6379");
        return Arrays.asList(endpoints.split(","));
    }
    
    public long getWriterIntervalMs() {
        return Long.parseLong(properties.getProperty("writer.interval.ms", "1000"));
    }
    
    public String getWriterKeyPrefix() {
        return properties.getProperty("writer.key.prefix", "latency");
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

    public int getBackgroundLoadReadWriteRatio() {
        return Integer.parseInt(properties.getProperty("background.load.read.write.ratio", "10"));
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
}

