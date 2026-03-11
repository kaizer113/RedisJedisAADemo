#!/bin/bash

# Redis Active-Active Demo Runner Script

echo "=========================================="
echo "Redis Active-Active Replication Demo"
echo "=========================================="
echo ""

# Build the project if needed
if [ ! -f "target/jedis-active-active-1.0.0.jar" ]; then
    echo "Building project..."
    mvn clean package -q
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
    echo "✅ Build successful"
    echo ""
fi

# Run the application
echo "Starting demo..."
echo "Press Ctrl+C to stop"
echo ""

java -jar target/jedis-active-active-1.0.0.jar

