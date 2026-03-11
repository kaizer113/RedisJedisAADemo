package com.redis.demo;

import com.redis.demo.config.ConfigManager;
import com.redis.demo.connection.RedisConnectionManager;
import com.redis.demo.metrics.MetricsCollector;
import com.redis.demo.threads.BackgroundLoadGenerator;
import com.redis.demo.threads.LatencyKeyReader;
import com.redis.demo.threads.LatencyKeyWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main application class for Redis Active-Active Replication Demo.
 * Orchestrates all threads and manages application lifecycle.
 */
public class RedisActiveActiveDemo {
    private static final Logger logger = LoggerFactory.getLogger(RedisActiveActiveDemo.class);

    private final ConfigManager config;
    private final RedisConnectionManager connectionManager;
    private final MetricsCollector metricsCollector;
    private final ConcurrentLinkedQueue<String> pendingKeys;

    private Thread writerThread;
    private Thread readerThread;
    private Thread metricsThread;
    private List<Thread> backgroundLoadThreads;

    private LatencyKeyWriter writer;
    private LatencyKeyReader reader;
    private List<BackgroundLoadGenerator> backgroundLoadGenerators;

    public RedisActiveActiveDemo() {
        this.config = new ConfigManager();
        this.connectionManager = new RedisConnectionManager(config);
        this.metricsCollector = new MetricsCollector(config.getMetricsIntervalSeconds());
        this.pendingKeys = new ConcurrentLinkedQueue<>();
    }
    
    public void start() {
        printBanner();

        // Connect connection manager to metrics collector for region display
        metricsCollector.setConnectionManager(connectionManager);

        // Start metrics collector
        metricsThread = new Thread(metricsCollector, "MetricsCollector");
        metricsThread.start();

        // Start latency key writer (shares pendingKeys queue with reader)
        writer = new LatencyKeyWriter(connectionManager.getWriterClient(), config, pendingKeys);
        writerThread = new Thread(writer, "LatencyKeyWriter");
        writerThread.start();

        // Start latency key reader (shares pendingKeys queue with writer)
        reader = new LatencyKeyReader(connectionManager.getReaderClient(), config, metricsCollector, pendingKeys);
        readerThread = new Thread(reader, "LatencyKeyReader");
        readerThread.start();

        // Connect writer and reader to metrics collector for ops/sec tracking
        metricsCollector.setLatencyKeyWriter(writer);
        metricsCollector.setLatencyKeyReader(reader);

        // Start background load generators if enabled
        if (config.isBackgroundLoadEnabled()) {
            int numThreads = config.getBackgroundLoadThreads();
            backgroundLoadGenerators = new ArrayList<>();
            backgroundLoadThreads = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                BackgroundLoadGenerator generator = new BackgroundLoadGenerator(
                    connectionManager.getWriterClient(), config);
                Thread thread = new Thread(generator, "BackgroundLoadGenerator-" + i);

                backgroundLoadGenerators.add(generator);
                backgroundLoadThreads.add(thread);
                thread.start();
            }

            // Connect background load generators to metrics collector
            metricsCollector.setBackgroundLoadGenerators(backgroundLoadGenerators);
        }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
    
    private void printBanner() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  REDIS ACTIVE-ACTIVE REPLICATION DEMO");
        System.out.println("=".repeat(80));
        System.out.println("Configuration:");

        // Show each endpoint on a separate line
        System.out.println("  Redis Endpoints:");
        List<String> endpoints = config.getRedisEndpoints();
        for (int i = 0; i < endpoints.size(); i++) {
            String endpoint = endpoints.get(i);
            String role = (i == 0) ? "Writer" : "Reader";
            // Simplify endpoint display (hide credentials)
            String displayEndpoint = endpoint.contains("@")
                ? endpoint.substring(endpoint.indexOf("@") + 1)
                : endpoint;
            System.out.println("    " + role + ": " + displayEndpoint);
        }

        System.out.println("  Writer Interval:     " + config.getWriterIntervalMs() + " ms");
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

        System.out.println("  Background Load:     " + (config.isBackgroundLoadEnabled() ? "ENABLED" : "DISABLED"));
        if (config.isBackgroundLoadEnabled()) {
            System.out.println("  Read/Write Ratio:    " + config.getBackgroundLoadReadWriteRatio() + ":1");
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
            for (BackgroundLoadGenerator generator : backgroundLoadGenerators) {
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
        
        // Close Redis connections
        connectionManager.close();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("  Demo stopped. Thank you!");
        System.out.println("=".repeat(80) + "\n");
    }
    
    public static void main(String[] args) {
        try {
            RedisActiveActiveDemo demo = new RedisActiveActiveDemo();
            demo.start();
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Fatal error in main application", e);
            System.exit(1);
        }
    }
}

