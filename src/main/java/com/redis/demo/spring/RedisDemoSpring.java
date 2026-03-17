package com.redis.demo.spring;

import com.redis.demo.spring.config.ConfigManagerSpring;
import com.redis.demo.spring.metrics.MetricsCollectorSpring;
import com.redis.demo.spring.threads.BackgroundLoadGeneratorSpring;
import com.redis.demo.spring.threads.LatencyKeyReaderSpring;
import com.redis.demo.spring.threads.LatencyKeyWriterSpring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spring Boot application for Redis Spring Data Demo.
 * Uses JedisConnectionFactory and StringRedisTemplate to exactly mirror customer's environment.
 * Connects to Redis Cloud endpoints to measure replication latency.
 */
@SpringBootApplication
@PropertySource("classpath:redis-spring.properties")
public class RedisDemoSpring implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(RedisDemoSpring.class);

    @Autowired
    @Qualifier("writerRedisTemplate")
    private StringRedisTemplate writerRedisTemplate;

    @Autowired
    @Qualifier("readerRedisTemplate")
    private StringRedisTemplate readerRedisTemplate;

    private final ConfigManagerSpring config;
    private final MetricsCollectorSpring metricsCollector;
    private final ConcurrentLinkedQueue<String> pendingKeys;

    private Thread writerThread;
    private Thread readerThread;
    private Thread metricsThread;
    private List<Thread> backgroundLoadThreads;

    private LatencyKeyWriterSpring writer;
    private LatencyKeyReaderSpring reader;
    private List<BackgroundLoadGeneratorSpring> backgroundLoadGenerators;

    public RedisDemoSpring() {
        this.config = new ConfigManagerSpring();
        this.metricsCollector = new MetricsCollectorSpring(config.getMetricsIntervalSeconds());
        this.pendingKeys = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void run(String... args) {
        printBanner();

        // Start metrics collector
        metricsThread = new Thread(metricsCollector, "MetricsCollectorSpring");
        metricsThread.start();

        // Start latency key writer (shares pendingKeys queue with reader)
        writer = new LatencyKeyWriterSpring(writerRedisTemplate, config, pendingKeys);
        writerThread = new Thread(writer, "LatencyKeyWriterSpring");
        writerThread.start();

        // Start latency key reader (shares pendingKeys queue with writer)
        reader = new LatencyKeyReaderSpring(readerRedisTemplate, config, metricsCollector, pendingKeys);
        readerThread = new Thread(reader, "LatencyKeyReaderSpring");
        readerThread.start();

        // Start background load generators if enabled
        if (config.isBackgroundLoadEnabled()) {
            int numThreads = config.getBackgroundLoadThreads();
            backgroundLoadGenerators = new ArrayList<>();
            backgroundLoadThreads = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                BackgroundLoadGeneratorSpring generator = new BackgroundLoadGeneratorSpring(
                    writerRedisTemplate, config);
                Thread thread = new Thread(generator, "BackgroundLoadGeneratorSpring-" + i);

                backgroundLoadGenerators.add(generator);
                backgroundLoadThreads.add(thread);
                thread.start();
            }

            // Connect background load generators to metrics collector
            metricsCollector.setBackgroundLoadGenerators(backgroundLoadGenerators);
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        // Keep the application running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Application interrupted");
        }
    }

    private void printBanner() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  REDIS SPRING DATA REPLICATION DEMO");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");
        System.out.println("  Connection:          Spring Boot with JedisConnectionFactory");
        System.out.println("  Pool Settings:       maxIdle=0, maxTotal=100 (customer config)");
        System.out.println("  Metrics Interval:    " + config.getMetricsIntervalSeconds() + " seconds");

        // Show value size and sample
        int valueSize = config.getValueSize();
        System.out.println("  Value Size:          " + valueSize + " bytes");

        // Create a sample value to show format
        long sampleMillis = System.currentTimeMillis();
        long sampleNanos = 123456;
        int paddingSize = Math.max(0, valueSize - 21);
        String samplePadding = "A".repeat(Math.min(10, paddingSize)) + "...";
        String sampleValue = sampleMillis + "." + sampleNanos + "_" + samplePadding;
        System.out.println("  Sample Value:        " + sampleValue);

        // Show background load configuration if enabled
        if (config.isBackgroundLoadEnabled()) {
            System.out.println("  Read/Write Ratio:    " + config.getBackgroundLoadReadWriteRatio() + ":1");
            System.out.println("  Threads:             " + config.getBackgroundLoadThreads());
        }
        System.out.println("  Key TTL:             " + config.getKeyTtlSeconds() + " seconds");
        System.out.println("=".repeat(80));
        System.out.println("Press Ctrl+C to stop the demo\n");
    }

    public void shutdown() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  Shutting down demo...");
        System.out.println("=".repeat(80));

        // Stop all threads
        if (writer != null) {
            writer.stop();
        }
        if (reader != null) {
            reader.stop();
        }
        if (metricsCollector != null) {
            metricsCollector.stop();
        }
        if (backgroundLoadGenerators != null) {
            for (BackgroundLoadGeneratorSpring generator : backgroundLoadGenerators) {
                generator.stop();
            }
        }

        // Wait for threads to finish
        try {
            if (writerThread != null) writerThread.join(5000);
            if (readerThread != null) readerThread.join(5000);
            if (metricsThread != null) metricsThread.join(5000);
            if (backgroundLoadThreads != null) {
                for (Thread thread : backgroundLoadThreads) {
                    thread.join(5000);
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for threads to finish", e);
            Thread.currentThread().interrupt();
        }

        // Spring will handle closing the Redis connections via JedisConnectionFactory

        System.out.println("\n" + "=".repeat(80));
        System.out.println("  Demo stopped. Thank you!");
        System.out.println("=".repeat(80) + "\n");
    }

    public static void main(String[] args) {
        SpringApplication.run(RedisDemoSpring.class, args);
    }
}

