package com.redis.demo.threads;

import com.redis.demo.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writer thread that creates latency measurement keys every second.
 * Adds written keys to a shared queue for the reader to process.
 */
public class LatencyKeyWriter implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(LatencyKeyWriter.class);

    private final JedisPooled jedis;
    private final ConfigManager config;
    private final AtomicBoolean running;
    private final AtomicLong counter;
    private final ConcurrentLinkedQueue<String> pendingKeys;
    private final String paddingString;

    public LatencyKeyWriter(JedisPooled jedis, ConfigManager config, ConcurrentLinkedQueue<String> pendingKeys) {
        this.jedis = jedis;
        this.config = config;
        this.running = new AtomicBoolean(true);
        this.counter = new AtomicLong(0);
        this.pendingKeys = pendingKeys;

        // Pre-generate padding string once (much more efficient than generating each time)
        // Timestamp format: "millis.nanos_" = ~21 bytes, so padding = total size - 21
        int paddingSize = Math.max(0, config.getValueSize() - 21);
        this.paddingString = generatePaddingString(paddingSize);
    }
    
    @Override
    public void run() {
        while (running.get()) {
            try {
                long currentCount = counter.incrementAndGet();
                String key = config.getWriterKeyPrefix() + ":" + currentCount;

                // Create value with high-precision timestamp + pre-generated padding
                String value = createValue();

                // Set key with TTL
                jedis.setex(key, config.getKeyTtlSeconds(), value);

                // Add key to pending queue for reader to process
                pendingKeys.offer(key);

                // Sleep for the configured interval
                Thread.sleep(config.getWriterIntervalMs());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error writing latency key", e);
                // Continue running even if there's an error
            }
        }
    }
    
    private String createValue() {
        // Use high-precision timestamp (millis + nanos for sub-millisecond accuracy)
        long millis = System.currentTimeMillis();
        long nanos = System.nanoTime() % 1_000_000;  // Get nanosecond offset within current millisecond

        // Format: "millis.nanos_padding"
        return millis + "." + nanos + "_" + paddingString;
    }

    private String generatePaddingString(int length) {
        // Generate static padding string once (much more efficient than random generation)
        StringBuilder sb = new StringBuilder(length);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(i % chars.length()));
        }

        return sb.toString();
    }
    
    public void stop() {
        running.set(false);
    }
    
    public long getCurrentCounter() {
        return counter.get();
    }
}

