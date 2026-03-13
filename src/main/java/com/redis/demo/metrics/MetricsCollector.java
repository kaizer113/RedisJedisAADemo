package com.redis.demo.metrics;

import com.redis.demo.connection.RedisConnectionManager;
import com.redis.demo.threads.BackgroundLoadGenerator;
import com.redis.demo.threads.LatencyKeyReader;
import com.redis.demo.threads.LatencyKeyWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collects and aggregates replication lag metrics.
 */
public class MetricsCollector implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final int intervalSeconds;
    private final AtomicBoolean running;
    private final ConcurrentLinkedQueue<LatencyMeasurement> measurements;
    private List<BackgroundLoadGenerator> backgroundLoadGenerators;
    private RedisConnectionManager connectionManager;
    private LatencyKeyWriter latencyKeyWriter;
    private LatencyKeyReader latencyKeyReader;

    private long lastReadCount = 0;
    private long lastWriteCount = 0;
    private long lastLatencyReaderCount = 0;
    private long lastLatencyWriterCount = 0;

    public MetricsCollector(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.running = new AtomicBoolean(true);
        this.measurements = new ConcurrentLinkedQueue<>();
    }

    public void setBackgroundLoadGenerators(List<BackgroundLoadGenerator> generators) {
        this.backgroundLoadGenerators = generators;
    }

    public void setConnectionManager(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void setLatencyKeyWriter(LatencyKeyWriter writer) {
        this.latencyKeyWriter = writer;
    }

    public void setLatencyKeyReader(LatencyKeyReader reader) {
        this.latencyKeyReader = reader;
    }
    
    public void recordLatency(String key, long latencyMs) {
        measurements.offer(new LatencyMeasurement(key, latencyMs, System.currentTimeMillis()));
    }
    
    @Override
    public void run() {
        while (running.get()) {
            try {
                Thread.sleep(intervalSeconds * 1000L);
                
                if (!measurements.isEmpty()) {
                    displayMetrics();
                }
                
            } catch (InterruptedException e) {
                logger.info("MetricsCollector interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in metrics collection", e);
            }
        }
        
        logger.info("MetricsCollector stopped");
    }
    
    private void displayMetrics() {
        List<LatencyMeasurement> snapshot = new ArrayList<>();
        LatencyMeasurement measurement;
        
        while ((measurement = measurements.poll()) != null) {
            snapshot.add(measurement);
        }
        
        if (snapshot.isEmpty()) {
            return;
        }
        
        MetricsSnapshot metrics = calculateMetrics(snapshot);
        printMetrics(metrics, snapshot.size());
    }
    
    private MetricsSnapshot calculateMetrics(List<LatencyMeasurement> measurements) {
        List<Long> latencies = new ArrayList<>();
        
        for (LatencyMeasurement m : measurements) {
            if (m.latencyMs >= 0) {  // Filter out invalid measurements
                latencies.add(m.latencyMs);
            }
        }
        
        if (latencies.isEmpty()) {
            return new MetricsSnapshot(0, 0, 0, 0);
        }
        
        Collections.sort(latencies);
        
        long sum = 0;
        for (long latency : latencies) {
            sum += latency;
        }
        
        double average = (double) sum / latencies.size();
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long p95 = calculatePercentile(latencies, 95);
        
        return new MetricsSnapshot(average, p95, max, min);
    }
    
    private long calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        
        return sortedValues.get(index);
    }
    
    private void printMetrics(MetricsSnapshot metrics, int sampleCount) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("REPLICATION LAG METRICS - " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("=".repeat(80));

        // Show endpoint health status
        if (connectionManager != null) {
            System.out.println("REDIS ENDPOINTS:");
            for (String region : connectionManager.getAllRegions()) {
                boolean isHealthy = connectionManager.isEndpointHealthy(region);
                String healthStatus = isHealthy ? "✅ HEALTHY" : "❌ UNHEALTHY";
                System.out.printf("  %s: %s%n", region, healthStatus);
            }
            System.out.println();
        }

        // Measure client latencies once
        double writerLatency = -1.0;
        double readerLatency = -1.0;

        // Show connection endpoints and measure client latencies
        if (connectionManager != null) {
            writerLatency = connectionManager.measureWriterLatency();
            readerLatency = connectionManager.measureReaderLatency();

            // Show active regions (may differ from configured after failover)
            String activeWriter = connectionManager.getActiveWriterRegion();
            String activeReader = connectionManager.getActiveReaderRegion();

            // Load Generator uses the Writer client
            System.out.printf("Load Generator:  %s (latency: %.2f ms)%n",
                            activeWriter, writerLatency);
            System.out.printf("Writer Region:   %s (client latency: %.2f ms)%n",
                            activeWriter, writerLatency);
            System.out.printf("Reader Region:   %s (client latency: %.2f ms)%n",
                            activeReader, readerLatency);
            System.out.println();
        }

        // Calculate network overhead and subtract from all metrics
        double networkOverhead = 0.0;
        if (connectionManager != null && writerLatency > 0 && readerLatency > 0) {
            networkOverhead = writerLatency + readerLatency;
        }

        // Display lag metrics with network overhead already subtracted
        System.out.printf("Average Lag:     %.2f ms%n", Math.max(0, metrics.average - networkOverhead));
        System.out.printf("P95 Lag:         %.2f ms%n", Math.max(0, metrics.p95 - networkOverhead));
        System.out.printf("Max Lag:         %.2f ms%n", Math.max(0, metrics.max - networkOverhead));
        System.out.printf("Min Lag:         %.2f ms%n", Math.max(0, metrics.min - networkOverhead));

        // Display background load statistics if available
        if (backgroundLoadGenerators != null && !backgroundLoadGenerators.isEmpty()) {
            // Aggregate stats from all background load generators
            long currentReads = 0;
            long currentWrites = 0;
            for (BackgroundLoadGenerator generator : backgroundLoadGenerators) {
                currentReads += generator.getReadCount();
                currentWrites += generator.getWriteCount();
            }

            long readsDelta = currentReads - lastReadCount;
            long writesDelta = currentWrites - lastWriteCount;

            System.out.println();
            System.out.printf("Reads:       %s /sec%n", formatWithSpaces(readsDelta / intervalSeconds));
            System.out.printf("Writes:      %s /sec%n", formatWithSpaces(writesDelta / intervalSeconds));
            System.out.printf("Total ops:   %s /sec%n",
                            formatWithSpaces((readsDelta + writesDelta) / intervalSeconds));

            // Update last counts for next interval
            lastReadCount = currentReads;
            lastWriteCount = currentWrites;
        }

        System.out.println("=".repeat(80) + "\n");
    }

    /**
     * Format a number with spaces as thousand separators for better readability.
     * Example: 15272 -> "15 272"
     */
    private String formatWithSpaces(long number) {
        String numStr = String.valueOf(number);
        StringBuilder result = new StringBuilder();
        int length = numStr.length();

        for (int i = 0; i < length; i++) {
            if (i > 0 && (length - i) % 3 == 0) {
                result.append(' ');
            }
            result.append(numStr.charAt(i));
        }

        return result.toString();
    }
    
    public void stop() {
        logger.info("Stopping MetricsCollector");
        running.set(false);
    }
    
    // Inner classes
    private static class LatencyMeasurement {
        final String key;
        final long latencyMs;
        final long timestamp;
        
        LatencyMeasurement(String key, long latencyMs, long timestamp) {
            this.key = key;
            this.latencyMs = latencyMs;
            this.timestamp = timestamp;
        }
    }
    
    private static class MetricsSnapshot {
        final double average;
        final long p95;
        final long max;
        final long min;
        
        MetricsSnapshot(double average, long p95, long max, long min) {
            this.average = average;
            this.p95 = p95;
            this.max = max;
            this.min = min;
        }
    }
}

