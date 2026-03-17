package com.redis.demo.spring.threads;

import com.redis.demo.spring.config.ConfigManagerSpring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates background load for OSS with GET/SET operations.
 * Uses Spring Data Redis StringRedisTemplate (customer's exact pattern).
 * Optimized for high throughput and realistic cache hit rates.
 * Uses a counter-based key strategy to ensure reads hit existing keys.
 */
public class BackgroundLoadGeneratorSpring implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BackgroundLoadGeneratorSpring.class);

    private final StringRedisTemplate redisTemplate;
    private final ConfigManagerSpring config;
    private final AtomicBoolean running;
    private final Random random;
    private final AtomicLong readCount;
    private final AtomicLong writeCount;
    private final String staticValue; // Pre-generated value, reused for all writes

    private static final String KEY_PREFIX = "bgload";
    private static final int KEY_RANGE = 1000; // Smaller range for better cache hit rate
    private final AtomicLong writeCounter; // Counter for sequential key writes

    public BackgroundLoadGeneratorSpring(StringRedisTemplate redisTemplate, ConfigManagerSpring config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
        this.running = new AtomicBoolean(true);
        this.random = new Random();
        this.readCount = new AtomicLong(0);
        this.writeCount = new AtomicLong(0);
        this.writeCounter = new AtomicLong(0);

        // Pre-generate static value once (huge performance improvement)
        this.staticValue = generateStaticValue();
    }
    
    @Override
    public void run() {
        int readWriteRatio = config.getBackgroundLoadReadWriteRatio();
        int operationCounter = 0;

        while (running.get()) {
            try {
                operationCounter++;

                // Determine if this should be a read or write based on ratio
                boolean shouldWrite = (operationCounter % (readWriteRatio + 1)) == 0;

                if (shouldWrite) {
                    performWrite();
                } else {
                    performRead();
                }

                // Small delay to control throughput
                // 100,000 ns = 0.1ms = ~10K ops/sec per thread
                Thread.sleep(0, 100_000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in background load generation", e);
                // Continue running even if there's an error
            }
        }
    }
    
    private void performRead() {
        try {
            // Use pipeline to send 2 reads in one network round trip
            long keyNumber1 = writeCounter.get() % KEY_RANGE;
            String key1 = KEY_PREFIX + ":" + keyNumber1;

            long keyNumber2 = (writeCounter.get() + 1) % KEY_RANGE;
            String key2 = KEY_PREFIX + ":" + keyNumber2;

            // Execute both reads in one network round trip using Spring's executePipelined
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                byte[] key1Bytes = redisTemplate.getStringSerializer().serialize(key1);
                byte[] key2Bytes = redisTemplate.getStringSerializer().serialize(key2);
                connection.get(key1Bytes);
                connection.get(key2Bytes);
                return null;
            });

            readCount.addAndGet(2); // Increment by 2 since we did 2 reads
        } catch (Exception e) {
            logger.debug("Error performing read", e);
        }
    }

    private void performWrite() {
        try {
            // Use pipeline to send 2 writes in one network round trip
            long keyNumber1 = writeCounter.incrementAndGet() % KEY_RANGE;
            String key1 = KEY_PREFIX + ":" + keyNumber1;

            long keyNumber2 = writeCounter.incrementAndGet() % KEY_RANGE;
            String key2 = KEY_PREFIX + ":" + keyNumber2;

            // Execute both writes in one network round trip using Spring's executePipelined
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                byte[] key1Bytes = redisTemplate.getStringSerializer().serialize(key1);
                byte[] key2Bytes = redisTemplate.getStringSerializer().serialize(key2);
                byte[] valueBytes = redisTemplate.getStringSerializer().serialize(staticValue);
                connection.setEx(key1Bytes, config.getKeyTtlSeconds(), valueBytes);
                connection.setEx(key2Bytes, config.getKeyTtlSeconds(), valueBytes);
                return null;
            });

            writeCount.addAndGet(2); // Increment by 2 since we did 2 writes
        } catch (Exception e) {
            logger.debug("Error performing write", e);
        }
    }

    /**
     * Generate a static value once at startup to avoid repeated string generation overhead.
     * This dramatically improves throughput by eliminating CPU cycles spent on random value generation.
     */
    private String generateStaticValue() {
        int valueSize = config.getValueSize();
        StringBuilder sb = new StringBuilder(valueSize);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < valueSize; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }
    
    public void stop() {
        running.set(false);
    }
    
    public long getReadCount() {
        return readCount.get();
    }
    
    public long getWriteCount() {
        return writeCount.get();
    }
}

