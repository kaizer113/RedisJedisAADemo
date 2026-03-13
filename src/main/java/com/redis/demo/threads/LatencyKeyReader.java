package com.redis.demo.threads;

import com.redis.demo.config.ConfigManager;
import com.redis.demo.connection.RedisConnectionManager;
import com.redis.demo.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reader thread that processes latency keys from a shared queue and calculates replication lag.
 * Uses MGET to batch-fetch multiple keys in a single request for efficiency.
 */
public class LatencyKeyReader implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(LatencyKeyReader.class);

    private static final int MAX_BATCH_SIZE = 100; // Maximum keys to fetch in one MGET

    private final UnifiedJedis jedis;
    private final ConfigManager config;
    private final MetricsCollector metricsCollector;
    private final RedisConnectionManager connectionManager;
    private final AtomicBoolean running;
    private final ConcurrentLinkedQueue<String> pendingKeys;
    private long processedCount;
    private final AtomicLong readCount; // Track total reads for ops/sec calculation

    public LatencyKeyReader(UnifiedJedis jedis, ConfigManager config, MetricsCollector metricsCollector,
                           RedisConnectionManager connectionManager, ConcurrentLinkedQueue<String> pendingKeys) {
        this.jedis = jedis;
        this.config = config;
        this.metricsCollector = metricsCollector;
        this.connectionManager = connectionManager;
        this.running = new AtomicBoolean(true);
        this.pendingKeys = pendingKeys;
        this.processedCount = 0;
        this.readCount = new AtomicLong(0);
    }

    public long getReadCount() {
        return readCount.get();
    }
    
    @Override
    public void run() {
        while (running.get()) {
            try {
                // Collect pending keys from the queue (up to MAX_BATCH_SIZE)
                List<String> keysToCheck = new ArrayList<>();
                int batchSize = 0;

                while (batchSize < MAX_BATCH_SIZE) {
                    String key = pendingKeys.poll();
                    if (key == null) {
                        break; // No more keys in queue
                    }
                    keysToCheck.add(key);
                    batchSize++;
                }

                if (!keysToCheck.isEmpty()) {
                    // Use MGET to fetch all keys in a single request
                    String[] keyArray = keysToCheck.toArray(new String[0]);
                    List<String> values = jedis.mget(keyArray);

                    // Track read count (MGET counts as reading multiple keys)
                    readCount.addAndGet(keyArray.length);

                    // Process results
                    List<String> notFoundKeys = new ArrayList<>();
                    for (int i = 0; i < keyArray.length; i++) {
                        String key = keyArray[i];
                        String value = values.get(i);

                        if (value != null) {
                            // Key found - calculate replication lag
                            long replicationLag = calculateReplicationLag(value);
                            metricsCollector.recordLatency(key, replicationLag);
                            processedCount++;

                        } else {
                            // Key not found yet (not replicated) - add back to queue for retry
                            notFoundKeys.add(key);
                        }
                    }

                    // Re-add keys that weren't found yet
                    if (!notFoundKeys.isEmpty()) {
                        pendingKeys.addAll(notFoundKeys);
                        //logger.debug("Re-queued {} keys not yet replicated", notFoundKeys.size());
                    }

                   // logger.debug("MGET batch: {} keys checked, {} found, {} re-queued",
                     //          keyArray.length, keyArray.length - notFoundKeys.size(), notFoundKeys.size());
                }

                // Check every 0.5ms for low latency measurement
                // Thread.sleep(millis, nanos) where 500,000 nanos = 0.5ms
                Thread.sleep(0, 500_000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error reading latency keys", e);
                // Continue running even if there's an error
            }
        }
    }
    
    private long calculateReplicationLag(String value) {
        try {
            // If writer and reader are using the same region, replication lag is 0
            String activeWriterRegion = connectionManager.getActiveWriterRegion();
            String activeReaderRegion = connectionManager.getActiveReaderRegion();

            if (activeWriterRegion != null && activeWriterRegion.equals(activeReaderRegion)) {
                return 0;
            }

            // Extract timestamp from value (format: millis.nanos_padding)
            String[] parts = value.split("_", 2);
            String timestampPart = parts[0];

            // Parse millis.nanos format
            String[] timestampParts = timestampPart.split("\\.", 2);
            long writeTimestampMillis = Long.parseLong(timestampParts[0]);

            // For replication lag, we only need millisecond precision
            // (nanoseconds are for sub-millisecond accuracy within same JVM)
            long currentTimestamp = System.currentTimeMillis();

            return currentTimestamp - writeTimestampMillis;
        } catch (Exception e) {
            logger.error("Error parsing timestamp from value: {}", value, e);
            return -1;
        }
    }
    
    public void stop() {
        running.set(false);
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public int getPendingQueueSize() {
        return pendingKeys.size();
    }
}

