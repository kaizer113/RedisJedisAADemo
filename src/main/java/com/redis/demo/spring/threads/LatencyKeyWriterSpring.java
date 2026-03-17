package com.redis.demo.spring.threads;

import com.redis.demo.spring.config.ConfigManagerSpring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writer thread for OSS that creates latency measurement keys every second.
 * Uses Spring Data Redis StringRedisTemplate (customer's exact pattern).
 * Adds written keys to a shared queue for the reader to process.
 */
public class LatencyKeyWriterSpring implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(LatencyKeyWriterSpring.class);

    private static final String KEY_PREFIX = "latency";

    // Session ID: seconds of current minute (0-59) to avoid reading stale keys from previous runs
    private static final String SESSION_ID = String.valueOf(LocalDateTime.now().getSecond());

    private final StringRedisTemplate redisTemplate;
    private final ConfigManagerSpring config;
    private final AtomicBoolean running;
    private final AtomicLong counter;
    private final ConcurrentLinkedQueue<String> pendingKeys;
    private final String paddingString;
    private final AtomicLong writeCount; // Track total writes for ops/sec calculation

    public LatencyKeyWriterSpring(StringRedisTemplate redisTemplate, ConfigManagerSpring config, ConcurrentLinkedQueue<String> pendingKeys) {
        this.redisTemplate = redisTemplate;
        this.config = config;
        this.running = new AtomicBoolean(true);
        this.counter = new AtomicLong(0);
        this.pendingKeys = pendingKeys;
        this.writeCount = new AtomicLong(0);

        // Pre-generate padding string once (much more efficient than generating each time)
        // Timestamp format: "millis.nanos_" = ~21 bytes, so padding = total size - 21
        int paddingSize = Math.max(0, config.getValueSize() - 21);
        this.paddingString = generatePaddingString(paddingSize);

        logger.info("LatencyKeyWriterSpring initialized with session ID: {}", SESSION_ID);
    }

    public long getWriteCount() {
        return writeCount.get();
    }
    
    @Override
    public void run() {
        while (running.get()) {
            try {
                long currentCount = counter.incrementAndGet();
                // Format: latency<SESSION_ID>:<counter>
                String key = KEY_PREFIX + SESSION_ID + ":" + currentCount;

                // Create value with high-precision timestamp + pre-generated padding
                String value = createValue();

                // Set key with TTL using StringRedisTemplate
                redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(config.getKeyTtlSeconds()));
                writeCount.incrementAndGet(); // Track write count

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

