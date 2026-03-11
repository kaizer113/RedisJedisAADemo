package com.redis.demo.threads;

import com.redis.demo.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates background load with GET/SET operations.
 * Optimized for high throughput and realistic cache hit rates.
 * Uses a counter-based key strategy to ensure reads hit existing keys.
 */
public class BackgroundLoadGenerator implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(BackgroundLoadGenerator.class);

    private final JedisPooled jedis;
    private final ConfigManager config;
    private final AtomicBoolean running;
    private final Random random;
    private final AtomicLong readCount;
    private final AtomicLong writeCount;
    private final String staticValue; // Pre-generated value, reused for all writes

    private static final String KEY_PREFIX = "bgload";
    private static final int KEY_RANGE = 1000; // Smaller range for better cache hit rate
    private final AtomicLong writeCounter; // Counter for sequential key writes

    public BackgroundLoadGenerator(JedisPooled jedis, ConfigManager config) {
        this.jedis = jedis;
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
                // Reduce sleep for higher throughput: 1ms = ~1K ops/sec, 0.1ms = ~10K ops/sec
                //Thread.sleep(0, 100_000);  // 0.1ms = 100,000 nanoseconds
                
            //} catch (InterruptedException e) {
            //    logger.info("BackgroundLoadGenerator interrupted");
            //    Thread.currentThread().interrupt();
            //    break;
            } catch (Exception e) {
                logger.error("Error in background load generation", e);
                // Continue running even if there's an error
            }
        }
    }
    
    private void performRead() {
        try {
            // Read from existing keys using modulo to ensure high cache hit rate
            long keyNumber = writeCounter.get() % KEY_RANGE;
            String key = KEY_PREFIX + ":" + keyNumber;
            jedis.get(key);
            readCount.incrementAndGet();
        } catch (Exception e) {
            logger.debug("Error performing read", e);
        }
    }

    private void performWrite() {
        try {
            // Sequential key writes for predictable key distribution
            long keyNumber = writeCounter.incrementAndGet() % KEY_RANGE;
            String key = KEY_PREFIX + ":" + keyNumber;

            // Reuse pre-generated static value (no overhead)
            jedis.setex(key, config.getKeyTtlSeconds(), staticValue);
            writeCount.incrementAndGet();
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

