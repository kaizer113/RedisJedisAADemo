package com.redis.demo.metrics;

import com.redis.demo.connection.RedisConnectionManager;
import com.redis.demo.threads.BackgroundLoadGenerator;
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
    private BackgroundLoadGenerator backgroundLoad;
    private RedisConnectionManager connectionManager;

    private long lastReadCount = 0;
    private long lastWriteCount = 0;

    public MetricsCollector(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.running = new AtomicBoolean(true);
        this.measurements = new ConcurrentLinkedQueue<>();
    }

    public void setBackgroundLoadGenerator(BackgroundLoadGenerator backgroundLoad) {
        this.backgroundLoad = backgroundLoad;
    }

    public void setConnectionManager(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    public void recordLatency(String key, long latencyMs) {
        measurements.offer(new LatencyMeasurement(key, latencyMs, System.currentTimeMillis()));
    }
    
    @Override
    public void run() {
        logger.info("MetricsCollector started - reporting every {} seconds", intervalSeconds);
        
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

        // Measure client latencies once
        double writerLatency = -1.0;
        double readerLatency = -1.0;

        // Show connection endpoints and measure client latencies
        if (connectionManager != null) {
            writerLatency = connectionManager.measureWriterLatency();
            readerLatency = connectionManager.measureReaderLatency();

            System.out.printf("Writer Region:   %s (client latency: %.2f ms)%n",
                            connectionManager.getWriterRegion(), writerLatency);
            System.out.printf("Reader Region:   %s (client latency: %.2f ms)%n",
                            connectionManager.getReaderRegion(), readerLatency);
            System.out.println("-".repeat(80));
        }

        System.out.printf("Sample Count:    %d measurements%n", sampleCount);
        System.out.printf("Average Lag:     %.2f ms%n", metrics.average);
        System.out.printf("P95 Lag:         %d ms%n", metrics.p95);
        System.out.printf("Max Lag:         %d ms%n", metrics.max);
        System.out.printf("Min Lag:         %d ms%n", metrics.min);

        // Calculate estimated true replication lag (subtract network overhead)
        if (connectionManager != null && writerLatency > 0 && readerLatency > 0) {
            double totalNetworkOverhead = writerLatency + readerLatency;
            double estimatedTrueReplicationLag = Math.max(0, metrics.average - totalNetworkOverhead);

            System.out.println("-".repeat(80));
            System.out.printf("Network Overhead: %.2f ms%n",
                            totalNetworkOverhead);
            System.out.printf("Est. True Lag:    %.2f ms%n",
                            estimatedTrueReplicationLag);
        }

        // Display background load statistics if available
        if (backgroundLoad != null) {
            long currentReads = backgroundLoad.getReadCount();
            long currentWrites = backgroundLoad.getWriteCount();
            long readsDelta = currentReads - lastReadCount;
            long writesDelta = currentWrites - lastWriteCount;

            System.out.println("-".repeat(80));
            System.out.println("BACKGROUND LOAD STATISTICS");
            System.out.println("-".repeat(80));
            System.out.printf("Total Reads:     %,d%n",
                            currentReads);
            System.out.printf("Total Writes:    %,d%n",
                            currentWrites);
            System.out.printf("Reads:       %.1f/sec%n", (double) readsDelta / intervalSeconds);
            System.out.printf("Writes:      %.1f/sec%n", (double) writesDelta / intervalSeconds);
            System.out.printf("Total ops:   %.1f/sec%n",
                            (double) (readsDelta + writesDelta) / intervalSeconds);

            // Update last counts for next interval
            lastReadCount = currentReads;
            lastWriteCount = currentWrites;
        }

        System.out.println("=".repeat(80) + "\n");
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

