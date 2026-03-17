#!/bin/bash

# Redis Spring Data Demo Runner Script

echo "=========================================="
echo "Redis Spring Data Replication Demo"
echo "=========================================="
echo ""

# Check for rebuild argument
if [ "$1" == "rebuild" ]; then
    echo "Rebuilding project..."
    mvn clean package -q
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
    echo "✅ Build successful"
    echo ""
# Build the project if JAR doesn't exist
elif [ ! -f "target/DemoSpring.jar" ]; then
    echo "Building project..."
    mvn clean package -q
    if [ $? -ne 0 ]; then
        echo "❌ Build failed"
        exit 1
    fi
    echo "✅ Build successful"
    echo ""
fi

# Check if config file exists
if [ ! -f "redis-spring.properties" ]; then
    echo "⚠️  Warning: redis-spring.properties not found in current directory"
    echo "   Using default configuration from JAR"
    echo ""
fi

# Run the application
echo "Starting Spring Data demo..."
echo "💡 Tip: Edit redis-spring.properties to change configuration without rebuilding"
echo "Press Ctrl+C to stop"
echo ""

java -jar target/DemoSpring.jar

