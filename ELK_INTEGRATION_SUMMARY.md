# ELK Integration Summary: MQTT5 Schema Validation Monitoring

## Overview

This integration adds operational visibility to your MQTT5 + Schema Registry solution by aggregating validation failures across all clients into a centralized ELK (Elasticsearch, Logstash, Kibana) stack.

## Problem Solved

**Before ELK Integration:**
- No visibility into which clients are producing invalid messages
- No way to identify patterns in validation failures
- Difficult to debug issues in distributed environments
- No operational metrics for message quality

**After ELK Integration:**
- Real-time dashboard showing validation failure rates
- Identify problematic clients and sensors immediately
- Historical trend analysis for capacity planning
- Automated alerts for operational issues
- Root cause analysis with categorized errors

## Architecture at a Glance

```
Publishers & Subscribers
        |
        | Emit structured JSON logs
        | [VALIDATION_EVENT] {"@timestamp": "...", "success": false, ...}
        |
        v
    Filebeat (on each host)
        |
        | Forward logs
        |
        v
    Logstash (centralized)
        |
        | Parse, enrich, filter
        |
        v
    Elasticsearch (indexed storage)
        |
        | Query & Aggregate
        |
        v
    Kibana (dashboards & alerts)
```

## Key Components

### 1. ValidationLogger.java
New utility class that emits structured JSON logs for every validation event:
- Publisher validation failures
- Subscriber deserialization errors
- Serialization failures
- Publish errors
- Successful validations (sampled)

### 2. ELK Stack Configuration
- **Filebeat** (`elk/filebeat.yml`): Collects logs from `/var/log/mqtt5/`
- **Logstash** (`elk/logstash.conf`): Parses and enriches logs
- **Elasticsearch** (`elk/elasticsearch-mapping.json`): Indexes validation events
- **Kibana** (`elk/kibana-dashboard.json`): Pre-built operations dashboard

### 3. Docker Compose Setup
One-command deployment for local development:
```bash
cd elk/
docker-compose up -d
```

## What Gets Logged

### Publisher-Side Events
```json
{
  "@timestamp": "2025-11-04T10:30:15.123Z",
  "event_type": "VALIDATION_FAILURE",
  "client_type": "PUBLISHER",
  "success": false,
  "message_id": "18",
  "schema_id": "solace/samples/tempsensor",
  "topic": "test/mqtt5/messages",
  "client_id": "mqtt5-publisher-1234567890",
  "broker_url": "ssl://mr-connection-xyz.messaging.solace.cloud:8883",
  "sensor_id": "sensor-010",
  "temperature": 200.0,
  "error_message": "Failed to validate schema: $: number found, number expected, maximum: 150.0",
  "error_category": "value_out_of_range",
  "hostname": "mqtt-client-01",
  "environment": "production"
}
```

### Subscriber-Side Events
```json
{
  "@timestamp": "2025-11-04T10:30:20.456Z",
  "event_type": "DESERIALIZATION_ERROR",
  "client_type": "SUBSCRIBER",
  "success": false,
  "message_id": "42",
  "schema_id": "solace/samples/tempsensor",
  "topic": "test/mqtt5/messages",
  "client_id": "mqtt5-subscriber-9876543210",
  "broker_url": "ssl://mr-connection-xyz.messaging.solace.cloud:8883",
  "sensor_id": "sensor-003",
  "error_message": "Failed to resolve schema for reference ArtifactRef",
  "error_category": "schema_not_found",
  "hostname": "mqtt-client-05",
  "environment": "production"
}
```

## Dashboard Metrics

### 1. Validation Failure Rate (Line Chart)
Tracks the number of validation failures over time (5-minute intervals)
- **Use Case**: Detect spikes in failures, correlate with deployments
- **Alert Threshold**: >5% failure rate over 10 minutes

### 2. Failure by Error Category (Pie Chart)
Breaks down failures by type:
- `missing_field`: Required fields not present
- `value_out_of_range`: Data exceeds min/max constraints
- `type_mismatch`: Wrong data type (string vs number)
- `schema_not_found`: Schema resolution failures
- `connectivity_issue`: Network/broker problems

### 3. Top 10 Failing Clients (Table)
Identifies which clients are producing the most invalid messages
- **Use Case**: Focus debugging efforts, identify misconfigured clients

### 4. Publisher vs Subscriber Failures (Bar Chart)
Compares where failures occur
- High publisher failures: Client-side validation working
- High subscriber failures: **Critical** - possible message corruption/tampering

### 5. Validation Success Rate (Gauge)
Real-time percentage of messages passing validation
- **Green** (>99.5%): Excellent
- **Yellow** (95-99.5%): Investigate
- **Red** (<95%): Critical issue

### 6. Recent Errors (Live Table)
Last 50 validation failures with full context
- **Use Case**: Real-time debugging, incident response

## Smart Sampling

To reduce log volume without losing critical information:

- **100% of failures** are logged (essential for debugging)
- **5% of successes** are sampled (sufficient for calculating rates)
- **All logs include context** (sensor ID, temperature, client ID)

This means:
- 1000 messages with 5% failure rate = 50 failure logs + ~48 success logs = 98 log entries
- Without sampling: 1000 log entries (10x more data)

## Deployment Options

### Option 1: Docker (Development)
```bash
cd elk/
./quick-start.sh
```
Cost: Free
Time: 5 minutes

### Option 2: Elastic Cloud (Recommended for Production)
- Sign up at https://cloud.elastic.co
- Managed service, no ops overhead
- Cost: ~$95/month (Standard tier)

### Option 3: AWS OpenSearch
- Integrated with AWS ecosystem
- Good if already using AWS for Schema Registry
- Cost: ~$200/month (3 nodes)

### Option 4: Self-Hosted on Kubernetes
- Deploy on same EKS cluster as Schema Registry
- Full control, cost-effective at scale
- Cost: ~$150/month (EKS costs only)

## Integration Steps

### Step 1: Add Structured Logging to Your Code

Update `MQTT5Publisher.java`:
```java
try {
    outBytes = serializer.serialize(schemaId, jsonNode, headers);
    // Log success (sampled)
    ValidationLogger.logSuccessfulValidation(...);
} catch (Exception e) {
    // Log failure (always)
    ValidationLogger.logPublisherValidationFailure(...);
}
```

Update `MQTT5Subscriber.java`:
```java
try {
    JsonNode data = deserializer.deserialize(topic, payload, headers);
    // Log success (sampled)
    ValidationLogger.logSuccessfulValidation(...);
} catch (Exception e) {
    // Log failure (always) - CRITICAL, possible corruption!
    ValidationLogger.logSubscriberValidationFailure(...);
}
```

### Step 2: Deploy ELK Stack

```bash
cd elk/
chmod +x quick-start.sh
./quick-start.sh
```

### Step 3: Run Your Application with Logging

```bash
# Create log directory
sudo mkdir -p /var/log/mqtt5
sudo chmod 777 /var/log/mqtt5

# Run publisher with logs redirected
mvn exec:java -Dexec.mainClass="MQTT5Publisher" \
  > /var/log/mqtt5/publisher.log 2>&1 &

# Run subscriber with logs redirected
mvn exec:java -Dexec.mainClass="MQTT5Subscriber" \
  > /var/log/mqtt5/subscriber.log 2>&1 &
```

### Step 4: Import Dashboard

1. Open Kibana: http://localhost:5601
2. Go to: **Management** > **Stack Management** > **Saved Objects**
3. Click **Import**
4. Upload: `elk/kibana-dashboard.json`
5. View: **Dashboard** > "MQTT5 Schema Validation Operations Dashboard"

## Alerting Examples

### Alert 1: High Failure Rate
```
Trigger: >5% of messages fail validation over 10 minutes
Action: 
  - Send Slack notification to #ops-alerts
  - Create PagerDuty incident
  - Email ops-team@company.com
```

### Alert 2: Critical Sensor Offline
```
Trigger: No messages from sensor-001 for 5 minutes
Action:
  - PagerDuty critical alert
  - SMS to on-call engineer
```

### Alert 3: Subscriber Validation Failures
```
Trigger: Any subscriber deserialization failure
Action:
  - Immediate Slack alert (potential security issue)
  - Log to security SIEM
Rationale: If publisher validated but subscriber failed, 
           message may be corrupted or tampered with
```

## Key Queries

### Find all failures in last hour:
```
success:false AND @timestamp:[now-1h TO now]
```

### Temperature sensor failures:
```
error_category:"value_out_of_range" AND sensor_id:sensor-*
```

### Publisher serialization errors:
```
client_type:PUBLISHER AND event_type:SERIALIZATION_ERROR
```

### Failures from specific client:
```
client_id:"mqtt5-publisher-12345" AND success:false
```

## Business Value

### For Operations Teams
- **Proactive Monitoring**: Identify issues before they impact customers
- **Faster Debugging**: Pinpoint failing clients and root causes
- **Capacity Planning**: Historical trends inform scaling decisions
- **SLA Tracking**: Measure message quality metrics

### For Development Teams
- **Schema Evolution**: Track impact of schema changes
- **Client QA**: Identify clients that need updates
- **Performance**: Correlate failures with load patterns
- **Security**: Detect message tampering/corruption

### For Business Stakeholders
- **Data Quality**: Quantify reliability of IoT data streams
- **Cost Optimization**: Reduce waste from invalid messages
- **Compliance**: Audit trail of data validation
- **Customer Experience**: Ensure high-quality sensor data

## Maintenance

### Daily Tasks
- Review dashboard for anomalies
- Respond to alerts

### Weekly Tasks
- Analyze top failing clients
- Review error category trends
- Adjust sampling rate if needed

### Monthly Tasks
- Review index size and optimize retention
- Update dashboard based on new requirements
- Audit alert thresholds

## Troubleshooting

### No data in Kibana?
1. Check logs are being written: `tail -f /var/log/mqtt5/*.log`
2. Verify Filebeat is running: `docker-compose logs filebeat`
3. Check Elasticsearch indices: `curl http://localhost:9200/_cat/indices?v`

### High log volume?
1. Increase sampling rate for successes (change from 5% to 1%)
2. Enable log compression in Filebeat
3. Adjust ILM policy to delete older data sooner

### Slow queries?
1. Check index health: `curl http://localhost:9200/_cluster/health`
2. Increase Elasticsearch memory
3. Reduce query time range

## Cost Analysis

### Development
- ELK in Docker: **Free**
- Operational effort: **Minimal** (5 min setup)

### Production (100 clients, 1M messages/day, 5% failure rate)
- **Elastic Cloud**: $95/month (Standard tier)
- **AWS OpenSearch**: $200/month (3 nodes)
- **Self-hosted on EKS**: $150/month (infrastructure only)

### Log Volume Estimate
- With sampling: ~50K log entries/day = ~1.5M/month
- Storage: ~5 GB/month (with 30-day retention)
- Elasticsearch sizing: 3 nodes, 8 GB RAM each

## Next Steps

1. **Review the example integration files**:
   - `MQTT5Publisher-ELK-Integration-Example.java`
   - `MQTT5Subscriber-ELK-Integration-Example.java`

2. **Run the quick start**:
   ```bash
   cd elk/
   ./quick-start.sh
   ```

3. **Generate test data**:
   Run your publisher/subscriber to generate validation events

4. **Explore Kibana**:
   - Create custom visualizations
   - Set up alerts
   - Share dashboards with your team

5. **Plan production deployment**:
   - Choose deployment option (Elastic Cloud vs self-hosted)
   - Configure authentication & SSL
   - Set up backup/snapshot strategy

## Support & Resources

- **ELK Documentation**: https://www.elastic.co/guide/
- **Solace Schema Registry**: https://docs.solace.com/Schema-Registry/
- **This Project**: See `elk/README.md` for detailed setup guide

---

**Key Takeaway**: By adding structured logging and the ELK stack, you transform your MQTT5 + Schema Registry solution from a "black box" into a fully observable, production-ready system with operational insights that help you maintain high data quality across thousands of distributed clients.

