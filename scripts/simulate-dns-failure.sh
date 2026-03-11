#!/bin/bash

# Script to simulate DNS failure by modifying /etc/hosts
# Usage: sudo ./simulate-dns-failure.sh <hostname>

if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: sudo $0 <hostname>"
    echo "Example: sudo $0 redis-primary.example.com"
    exit 1
fi

HOSTNAME=$1
HOSTS_FILE="/etc/hosts"
BACKUP_FILE="/etc/hosts.backup.$(date +%s)"

# Backup hosts file
cp $HOSTS_FILE $BACKUP_FILE
echo "✅ Backed up $HOSTS_FILE to $BACKUP_FILE"

# Add entry to block hostname
echo "127.0.0.1  $HOSTNAME  # REDIS_DEMO_DNS_FAILURE" >> $HOSTS_FILE
echo "✅ Added DNS failure entry for $HOSTNAME"
echo ""
echo "DNS failure simulated. The hostname $HOSTNAME now resolves to 127.0.0.1"
echo ""
echo "To restore, run: sudo ./restore-dns.sh"

