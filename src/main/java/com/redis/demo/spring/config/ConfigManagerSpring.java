package com.redis.demo.spring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for Redis OSS demo.
 * Reads configuration from redis-spring.properties file.
 */
public class ConfigManagerSpring {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManagerSpring.class);
    private static final String CONFIG_FILE = "redis-spring.properties";

    private final Properties properties;

    public ConfigManagerSpring() {
        this.properties = new Properties();
        loadProperties();
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + CONFIG_FILE);
            }
            properties.load(input);
            logger.info("Loaded configuration from {}", CONFIG_FILE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public int getWriterIntervalMs() {
        return Integer.parseInt(properties.getProperty("writer.interval.ms", "1000"));
    }

    public int getMetricsIntervalSeconds() {
        return Integer.parseInt(properties.getProperty("metrics.interval.seconds", "10"));
    }

    public int getKeyTtlSeconds() {
        return Integer.parseInt(properties.getProperty("key.ttl.seconds", "300"));
    }

    public int getValueSize() {
        return Integer.parseInt(properties.getProperty("value.size", "500"));
    }

    public boolean isBackgroundLoadEnabled() {
        return Boolean.parseBoolean(properties.getProperty("background.load.enabled", "true"));
    }

    public int getBackgroundLoadThreads() {
        return Integer.parseInt(properties.getProperty("background.load.threads", "4"));
    }

    public int getBackgroundLoadReadWriteRatio() {
        return Integer.parseInt(properties.getProperty("background.load.read.write.ratio", "10"));
    }
}

