# Redis Active-Active Replication Demo

## What This Demo Shows

This demo helps you **see and understand** how Redis Active-Active replication works across different geographic regions. It measures how quickly data written in one region appears in another region.

**Think of it like this**: You write a note in New York, and this demo measures how long it takes for someone in Ohio to see that same note.

## What You'll See

When you run the demo, you'll see metrics displayed every 15 seconds that look like this:

```
================================================================================
REPLICATION LAG METRICS - 2026-03-11T11:30:45
================================================================================
Writer Region:   us-east-1-mz (client latency: 25.34 ms)
Reader Region:   us-east-2-mz (client latency: 45.67 ms)
--------------------------------------------------------------------------------
Sample Count:    15 measurements
Average Lag:     95.23 ms
P95 Lag:         125 ms
Max Lag:         150 ms
Min Lag:         75 ms
--------------------------------------------------------------------------------
Network Overhead: 71.01 ms (writer 25.34 ms + reader 45.67 ms)
Est. True Lag:    24.22 ms (measured - network overhead)
================================================================================
BACKGROUND LOAD STATISTICS
--------------------------------------------------------------------------------
Total Reads:     135,000 (+ 13,500 in last 15s)
Total Writes:    13,500 (+ 1,350 in last 15s)
Reads/sec:       900.0
Writes/sec:      90.0
Total ops/sec:   990.0
================================================================================
```

## Understanding the Metrics

### What Each Number Means

**Writer Region / Reader Region**
- Shows which AWS regions are being used
- Writer = where data is created
- Reader = where we check if data has arrived
- Client latency = network round-trip time to each region

**Average Lag**
- The typical time it takes for data to replicate from writer to reader
- **Important**: This includes network latency!

**P95 Lag (95th Percentile)**
- 95% of replications are faster than this
- Helps you understand "worst case" performance for most operations

**Max/Min Lag**
- The slowest and fastest replication times observed

**Network Overhead**
- The total time spent just communicating over the network
- This is NOT replication time - it's just network "travel time"

**Est. True Lag**
- The actual replication lag after removing network overhead
- **This is the real number** - how long Redis takes to replicate data

### The Key Insight

In the example above:
- **Measured Lag**: 95.23 ms (what we observe)
- **Network Overhead**: 71.01 ms (just network communication)
- **True Replication**: 24.22 ms (actual Redis replication)

This means **~75% of the "lag" is just network latency**, not Redis replication!

## Quick Start

### Prerequisites
- Java 21 or later
- Maven
- Access to Redis Cloud Active-Active database (credentials in `redis.properties`)

### Running the Demo

```bash
# Build the project
mvn clean package

# Run the demo
./run.sh
```

The demo will start and display metrics every 15 seconds.

## What's Happening Behind the Scenes

### 1. Writing Data (us-east-1)
Every second, the demo writes a special key to Redis in the US East (Virginia) region. Each key contains:
- A high-precision timestamp (when it was written)
- Some padding data (to simulate real-world data size)

### 2. Reading Data (us-east-2)
The demo continuously checks the US East (Ohio) region to see if the key has appeared yet.

### 3. Measuring the Lag
When the key appears in Ohio, the demo calculates:
- How long it took to replicate (current time - timestamp in the key)
- Network latency to both regions (using PING)
- True replication lag (total lag - network latency)

### 4. Background Load
To simulate a real application, the demo also runs ~10,000 random read/write operations per second in the background. This shows how replication performs under realistic load.

## Changing Configuration

You can edit `redis.properties` in the project root directory **without rebuilding**:

```properties
# How often to write test keys (milliseconds)
writer.interval.ms=1000

# How often to show metrics (seconds)
metrics.interval.seconds=15

# Size of test data (bytes)
value.size=500

# Enable/disable background load
background.load.enabled=true

# Read/write ratio for background load
background.load.read.write.ratio=10
```

After editing, just run `./run.sh` again - no rebuild needed!

## What This Tells You

### About Your Redis Setup
- **How fast** data replicates between regions
- **How consistent** replication performance is (P95 vs Average)
- **How much** of the lag is network vs. actual replication

### About Your Application
- Whether cross-region replication is fast enough for your use case
- What latency your users in different regions will experience
- How background load affects replication performance

## Common Questions

**Q: Why is the lag higher than I expected?**
A: Check the "Network Overhead" - most of the lag is usually network latency, not Redis replication.

**Q: What's a good replication lag?**
A: It depends on your use case, but for cross-region Active-Active:
- 20-50ms true replication lag is excellent
- 50-100ms is good
- 100ms+ may indicate network issues or high load

**Q: Why does the lag vary?**
A: Network latency varies based on internet conditions, load on Redis, and other factors. That's why we show P95, Max, and Min.

**Q: Can I test with my own Redis database?**
A: Yes! Just update the `redis.endpoints` in `redis.properties` with your connection strings.

## Technical Details

For developers and DevOps engineers who want to understand the implementation details, architecture, and optimization strategies, see [Project.md](Project.md).

### Key Technical Features
- Java 21 with high-performance threading
- Producer-consumer pattern with shared queue
- MGET batching for efficient reads
- High-precision timestamps (millisecond + nanosecond)
- Network RTT measurement via PING
- SSL/TLS support for Redis Cloud
- External configuration (no rebuild required)

## Troubleshooting

**Problem: High measured lag**
- Check the "Network Overhead" value - it might just be network latency
- Look at "Est. True Lag" for the actual replication performance

**Problem: Connection errors**
- Verify your `redis.properties` has the correct endpoints
- Check that your Redis Cloud credentials are correct
- Ensure SSL/TLS is properly configured

**Problem: No metrics showing**
- Wait at least 15 seconds for the first metrics report
- Check that the writer is successfully creating keys
- Verify both Redis endpoints are accessible

## Support

For technical questions or issues:
- See [Project.md](Project.md) for detailed technical documentation
- Contact your Redis support team
- Check Redis Cloud documentation at https://redis.io/docs/

