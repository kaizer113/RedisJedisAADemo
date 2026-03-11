# Updating the Redis Active-Active Demo on Ubuntu

## Quick Update

If you already have the repository cloned on your Ubuntu machine:

```bash
# Navigate to the project directory
cd /path/to/Jedis-Active-Active

# Pull the latest changes
git pull origin main

# Rebuild the project
mvn clean package

# Run the application
./run.sh rebuild
```

## Fresh Installation

If you need to clone the repository for the first time:

```bash
# Clone the repository
git clone https://github.com/kaizer113/RedisJedisAADemo.git
cd RedisJedisAADemo

# Copy the sample properties file and configure it
cp redis.properties.sample redis.properties

# Edit redis.properties with your Redis endpoints
nano redis.properties
# or
vi redis.properties

# Build the project
mvn clean package

# Run the application
./run.sh rebuild
```

## Configuration

After pulling the latest changes, make sure to update your `redis.properties` file with your Redis Cloud endpoints:

```properties
# Redis Endpoints (comma-separated for Active-Active failover)
# Format: [username]:[password]@host:port,[username]:[password]@host:port
redis.endpoints=your-endpoint-1:port,your-endpoint-2:port
```

## What's New in This Update

- ✅ **MultiDbClient** - Automatic geographic failover support
- ✅ **Circuit Breaker** - Automatic failure detection
- ✅ **Retry Logic** - Exponential backoff for transient failures
- ✅ **Automatic Failback** - Returns to preferred endpoint when recovered
- ✅ **Geographic Routing** - Writer prefers East, Reader prefers West
- ✅ **Failover Logging** - Visibility into failover events

## Prerequisites

Make sure you have the following installed on Ubuntu:

```bash
# Java 21
java -version

# Maven
mvn -version

# If not installed:
sudo apt update
sudo apt install openjdk-21-jdk maven -y
```

## Troubleshooting

### If you have local changes to redis.properties

```bash
# Stash your local changes
git stash

# Pull the latest changes
git pull origin main

# Restore your local changes
git stash pop

# Or manually merge if there are conflicts
```

### If the build fails

```bash
# Clean Maven cache and rebuild
mvn clean install -U

# Or force update dependencies
mvn clean package -U
```

### If you get "NoClassDefFoundError: resilience4j"

Make sure you're using the latest `pom.xml` which includes `resilience4j-all`:

```bash
# Verify the dependency is in pom.xml
grep -A 3 "resilience4j-all" pom.xml

# Should show:
# <dependency>
#     <groupId>io.github.resilience4j</groupId>
#     <artifactId>resilience4j-all</artifactId>
#     <version>1.7.1</version>
# </dependency>
```

## Testing Failover

Once running, you can test the failover by:

1. **Simulate endpoint failure** - Block network access to one Redis endpoint
2. **Watch the logs** - Look for failover event messages
3. **Verify continuity** - Application should continue running on backup endpoint
4. **Restore endpoint** - Unblock network access
5. **Watch failback** - Application should return to preferred endpoint

## Support

For issues or questions, check the project documentation:
- `README.md` - Project overview
- `QUICKSTART.md` - Quick start guide
- `IMPLEMENTATION_SUMMARY.md` - Technical details

