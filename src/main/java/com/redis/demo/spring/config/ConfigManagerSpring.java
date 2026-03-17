package com.redis.demo.spring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration manager for Redis Spring demo.
 * Reads configuration from redis-spring.properties file.
 * First tries to load from current directory, then falls back to classpath.
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
        // First try to load from current directory (external file)
        Path externalConfig = Paths.get(CONFIG_FILE);
        if (Files.exists(externalConfig)) {
            try (InputStream input = new FileInputStream(externalConfig.toFile())) {
                properties.load(input);
                logger.info("Loaded configuration from external file: {}", externalConfig.toAbsolutePath());
                return;
            } catch (IOException e) {
                logger.warn("Failed to load external config file, falling back to classpath", e);
            }
        }

        // Fall back to classpath resource
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + CONFIG_FILE + " in classpath or current directory");
            }
            properties.load(input);
            logger.info("Loaded configuration from classpath: {}", CONFIG_FILE);
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

