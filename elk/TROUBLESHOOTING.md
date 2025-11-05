# ELK Stack Troubleshooting Guide

## Common Issues and Solutions

### 1. "No space left on device" when starting Docker

**Symptom:**
```
writing blob: adding layer with blob "sha256:...": 
processing tar file(write /usr/share/...: no space left on device): exit status 1
```

**Cause:** Docker Desktop's virtual disk is full (not your laptop's disk).

**Solutions:**

#### Quick Fix: Clean up Docker
```bash
# Remove all unused Docker resources
docker system prune -a --volumes

# Check what's using space
docker system df
```

#### Permanent Fix: Increase Docker disk allocation
1. Open **Docker Desktop**
2. Go to **Settings** (⚙️ icon)
3. Click **Resources** → **Advanced**
4. Increase **Virtual disk limit** to at least **60 GB**
5. Click **Apply & Restart**

#### Alternative: Use lightweight ELK configuration
```bash
# Edit docker-compose.yml to reduce memory:
# Change Elasticsearch from 512m to 256m
- "ES_JAVA_OPTS=-Xms256m -Xmx256m"

# Remove Logstash if not needed (use direct output to Elasticsearch)
```

---

### 2. Services won't start or keep restarting

**Check service status:**
```bash
docker-compose ps
docker-compose logs elasticsearch
docker-compose logs kibana
```

**Common causes:**

#### Elasticsearch fails with memory errors
```bash
# Increase Docker memory allocation in Docker Desktop
# Settings → Resources → Memory: 8 GB (minimum for ELK)
```

#### Port conflicts
```bash
# Check if ports are already in use
lsof -i :9200  # Elasticsearch
lsof -i :5601  # Kibana
lsof -i :5044  # Logstash

# Stop conflicting processes or change ports in docker-compose.yml
```

#### File permissions
```bash
# Elasticsearch needs write access to data directory
sudo chown -R 1000:1000 elasticsearch-data/
```

---

### 3. No data appearing in Kibana

**Step-by-step diagnosis:**

#### Check if logs are being written
```bash
tail -f /var/log/mqtt5/publisher.log | grep VALIDATION_EVENT
```
Expected: JSON log entries with `[VALIDATION_EVENT]`

#### Check if Filebeat is collecting logs
```bash
docker-compose logs filebeat | grep "Harvester started"
```

#### Check if Elasticsearch is receiving data
```bash
# List all indices
curl http://localhost:9200/_cat/indices?v

# Should see indices like: mqtt5-validation-2025.11.04
```

#### Check if data is in Elasticsearch
```bash
# Search for validation events
curl -X GET "http://localhost:9200/mqtt5-validation-*/_search?size=10&pretty"
```

#### Create index pattern in Kibana
1. Open Kibana: http://localhost:5601
2. Go to **Management** → **Stack Management** → **Kibana** → **Data Views**
3. Click **Create data view**
4. Index pattern: `mqtt5-validation-*`
5. Time field: `@timestamp`
6. Click **Save**

---

### 4. Filebeat "Failed to publish events" error

**Check Logstash connectivity:**
```bash
# Verify Logstash is listening
docker-compose logs logstash | grep "Started Logstash API endpoint"

# Test connection
telnet localhost 5044
```

**Alternative: Skip Logstash, send directly to Elasticsearch**

Edit `elk/filebeat.yml`:
```yaml
# Comment out Logstash output
# output.logstash:
#   hosts: ["localhost:5044"]

# Enable Elasticsearch output
output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "mqtt5-validation-%{+yyyy.MM.dd}"
```

Then restart:
```bash
docker-compose restart filebeat
```

---

### 5. High memory usage / laptop slowing down

**Reduce ELK resource usage:**

Edit `elk/docker-compose.yml`:

```yaml
elasticsearch:
  environment:
    # Reduce from 512m to 256m
    - "ES_JAVA_OPTS=-Xms256m -Xmx256m"

logstash:
  environment:
    # Reduce from 256m to 128m
    - "LS_JAVA_OPTS=-Xms128m -Xmx128m"
```

**Run only what you need:**
```bash
# Start only Elasticsearch and Kibana (skip Logstash)
docker-compose up -d elasticsearch kibana filebeat
```

---

### 6. Cannot access Kibana at localhost:5601

**Check Kibana is running:**
```bash
docker-compose ps kibana
docker-compose logs kibana
```

**Wait for Kibana to initialize** (can take 2-3 minutes):
```bash
# Check health endpoint
curl http://localhost:5601/api/status

# Wait until you see: "status":"green"
```

**Check firewall:**
```bash
# macOS
sudo lsof -i :5601

# If blocked, allow in System Preferences → Security & Privacy → Firewall
```

---

### 7. Validation events not being logged by application

**Verify ValidationLogger is being called:**

Add debug output to your code:
```java
System.out.println("About to log validation event...");
ValidationLogger.logPublisherValidationFailure(...);
System.out.println("Validation event logged");
```

**Check if logs are reaching stdout:**
```bash
# Run without background mode
mvn exec:java -Dexec.mainClass="MQTT5Publisher"

# Should see: [VALIDATION_EVENT] {...}
```

**Verify log file permissions:**
```bash
ls -la /var/log/mqtt5/
# Should be writable by your user
```

---

### 8. Index template not applying

**Verify template was created:**
```bash
curl http://localhost:9200/_index_template/mqtt5-validation-template?pretty
```

**Delete and recreate template:**
```bash
# Delete existing template
curl -X DELETE http://localhost:9200/_index_template/mqtt5-validation-template

# Delete existing indices
curl -X DELETE http://localhost:9200/mqtt5-validation-*

# Recreate template
curl -X PUT "http://localhost:9200/_index_template/mqtt5-validation-template" \
  -H 'Content-Type: application/json' \
  -d @elasticsearch-mapping.json
```

---

### 9. Docker Desktop not responding on macOS

**Restart Docker Desktop:**
```bash
# Kill Docker
killall Docker

# Restart from Applications
open -a Docker
```

**Or use CLI:**
```bash
# Stop
docker-compose down

# Restart Docker Desktop service
launchctl stop com.docker.docker
launchctl start com.docker.docker
```

---

### 10. M1/M2 Mac (ARM) compatibility issues

The ELK images should work on ARM, but if you see platform warnings:

**Force AMD64 platform:**

Edit `elk/docker-compose.yml`:
```yaml
services:
  elasticsearch:
    platform: linux/amd64  # Add this line
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    # ...
```

**Or use ARM-native alternatives:**
- Use AWS OpenSearch instead (cloud-based)
- Use Elastic Cloud (fully managed)

---

## Performance Tuning

### For laptops with limited resources:

```yaml
# Minimal configuration for development
# Edit elk/docker-compose.yml:

elasticsearch:
  environment:
    - discovery.type=single-node
    - "ES_JAVA_OPTS=-Xms256m -Xmx256m"  # Reduce memory
    - node.store.allow_mmap=false        # Reduce disk I/O

# Skip Logstash entirely:
# Delete the logstash service

# Update filebeat to output directly to Elasticsearch
```

### Sample rate optimization:

In `ValidationLogger.java`:
```java
// Change from 5% to 1% to reduce log volume
if (Math.random() < 0.01) {  // was 0.05
    logValidationEvent(...);
}
```

---

## Getting Help

### Check all logs at once:
```bash
docker-compose logs --tail=100 -f
```

### Get Docker info:
```bash
docker info
docker version
docker system df
```

### Verify application is logging:
```bash
# Watch logs in real-time
tail -f /var/log/mqtt5/*.log

# Count validation events
grep -c "VALIDATION_EVENT" /var/log/mqtt5/*.log
```

### Reset everything:
```bash
# Nuclear option: remove all Docker data
docker-compose down -v
docker system prune -a --volumes
rm -rf elasticsearch-data/ filebeat-data/

# Then start fresh
./quick-start.sh
```

---

## Alternative: Cloud-Based ELK

If Docker Desktop continues to have issues, consider using a cloud-based solution:

### Elastic Cloud (Easiest)
1. Sign up: https://cloud.elastic.co (14-day free trial)
2. Create deployment (takes 5 minutes)
3. Update `filebeat.yml` with cloud endpoint
4. No Docker needed!

### AWS OpenSearch
1. Create domain in AWS Console
2. Configure security groups
3. Update filebeat to send to OpenSearch endpoint

See `elk/README.md` for full cloud deployment instructions.

