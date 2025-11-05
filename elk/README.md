# ELK Stack Integration for MQTT5 Schema Validation Monitoring

This directory contains the configuration for integrating your MQTT5 schema validation application with the ELK (Elasticsearch, Logstash, Kibana) stack to provide operational visibility into message quality and validation failures.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kibana Dashboard                          │
│  (Visualizations, Alerts, Operations Insights)              │
└─────────────────────────────────────────────────────────────┘
                             ▲
                             │
┌─────────────────────────────────────────────────────────────┐
│                      Elasticsearch                           │
│  (Indexed validation events, time-series data)              │
└─────────────────────────────────────────────────────────────┘
                             ▲
                             │
┌─────────────────────────────────────────────────────────────┐
│                       Logstash                               │
│  (Parse, enrich, filter validation events)                  │
└─────────────────────────────────────────────────────────────┘
                             ▲
                             │
┌─────────────────────────────────────────────────────────────┐
│                       Filebeat                               │
│  (Collect logs from MQTT5 clients)                          │
└─────────────────────────────────────────────────────────────┘
                             ▲
                             │
┌─────────────────────────────────────────────────────────────┐
│         MQTT5 Publishers & Subscribers                       │
│  (Emit structured JSON logs with validation events)         │
└─────────────────────────────────────────────────────────────┘
```

## What Gets Monitored

### Publisher-Side Events
- **Validation Failures**: Invalid messages rejected before publish
- **Serialization Errors**: Schema serialization issues
- **Publish Errors**: MQTT broker connectivity issues

### Subscriber-Side Events
- **Deserialization Failures**: Unable to parse messages
- **Validation Failures**: Messages that fail schema validation (corruption/tampering)
- **Processing Errors**: Business logic failures

### Key Metrics
- Validation failure rate (%)
- Failures by error category (missing fields, out of range, type mismatch, etc.)
- Top failing clients
- Publisher vs subscriber failures
- Mean time between failures (MTBF)
- Geographic distribution of failures

## Quick Start (Local Development)

### 1. Start the ELK Stack

```bash
cd elk/
docker-compose up -d
```

Wait for all services to become healthy (about 2-3 minutes):

```bash
docker-compose ps
```

### 2. Verify Services

- **Elasticsearch**: http://localhost:9200
  ```bash
  curl http://localhost:9200/_cluster/health
  ```

- **Kibana**: http://localhost:5601
  Open in browser

- **Logstash**: http://localhost:9600
  ```bash
  curl http://localhost:9600
  ```

### 3. Create Elasticsearch Index Template

```bash
curl -X PUT "http://localhost:9200/_index_template/mqtt5-validation-template" \
  -H 'Content-Type: application/json' \
  -d @elasticsearch-mapping.json
```

### 4. Configure Application Logging

Update your application to write logs to `/var/log/mqtt5/`:

```bash
# Create log directory
sudo mkdir -p /var/log/mqtt5
sudo chmod 777 /var/log/mqtt5

# Run publisher with logging
cd ..
mvn clean compile
mvn exec:java -Dexec.mainClass="MQTT5Publisher" > /var/log/mqtt5/publisher.log 2>&1 &

# Run subscriber with logging
mvn exec:java -Dexec.mainClass="MQTT5Subscriber" > /var/log/mqtt5/subscriber.log 2>&1 &
```

### 5. Import Kibana Dashboard

1. Open Kibana at http://localhost:5601
2. Go to **Management** > **Stack Management** > **Kibana** > **Saved Objects**
3. Click **Import**
4. Upload `kibana-dashboard.json`
5. Go to **Dashboard** and open "MQTT5 Schema Validation Operations Dashboard"

## Production Deployment

### Option 1: Elastic Cloud (Recommended)

1. Sign up at https://cloud.elastic.co
2. Create a deployment (Elasticsearch + Kibana)
3. Update `filebeat.yml` with your cloud credentials:
   ```yaml
   output.elasticsearch:
     hosts: ["your-deployment.es.us-east-1.aws.found.io:9243"]
     username: "elastic"
     password: "your-password"
     protocol: "https"
   ```

### Option 2: Self-Hosted on Kubernetes

Deploy ELK on the same EKS cluster as your Schema Registry:

```bash
# Add Elastic Helm repo
helm repo add elastic https://helm.elastic.co
helm repo update

# Deploy Elasticsearch
helm install elasticsearch elastic/elasticsearch \
  --namespace monitoring \
  --create-namespace \
  --set replicas=3 \
  --set volumeClaimTemplate.resources.requests.storage=100Gi

# Deploy Kibana
helm install kibana elastic/kibana \
  --namespace monitoring \
  --set service.type=LoadBalancer

# Deploy Filebeat as DaemonSet
helm install filebeat elastic/filebeat \
  --namespace monitoring \
  --set-file filebeatConfig.filebeat\\.yml=filebeat.yml
```

### Option 3: AWS OpenSearch Service

If you're already on AWS:

```bash
# Create OpenSearch domain via AWS Console or CloudFormation
# Update filebeat.yml output to:
output.elasticsearch:
  hosts: ["your-opensearch-domain.us-east-2.es.amazonaws.com:443"]
  protocol: "https"
  aws_auth:
    region: "us-east-2"
```

## Dashboard Features

### 1. Real-Time Monitoring
- Live feed of validation failures (last 15 minutes)
- Validation success rate gauge with color-coded thresholds
- Message flow visualization

### 2. Historical Analysis
- Validation failure trends (24-hour view)
- Failure rate by error category (pie chart)
- Top 10 failing clients
- Publisher vs subscriber failure comparison

### 3. Business Intelligence
- Validation failures by sensor ID (heat map)
- Temperature readings correlation with failures
- Geographic distribution of failures (if using cloud brokers)

### 4. Alerting

The dashboard includes pre-configured alerts:

- **High Validation Failure Rate**: Triggers when >5% of messages fail validation over 10 minutes
- **Critical Sensor Offline**: Alerts when critical sensors stop sending data

Configure alert actions in Kibana:
- Slack notifications
- Email alerts
- PagerDuty integration
- Webhook to custom systems

## Log Sampling Strategy

To reduce log volume in production, the application uses **smart sampling**:

- **100% of failures** are logged (critical for debugging)
- **5% of successes** are sampled (sufficient for calculating success rate)
- **All validation events** include rich context (sensor ID, temperature, etc.)

Adjust sampling rate in `ValidationLogger.java`:

```java
// Sample 1% instead of 5%
if (Math.random() < 0.01) {
    // Log successful validation
}
```

## Key Queries

### Find All Validation Failures (Last Hour)
```
success:false AND @timestamp:[now-1h TO now]
```

### Temperature Out of Range Errors
```
error_category:"value_out_of_range" AND sensor_id:*
```

### Publisher Serialization Failures
```
client_type:PUBLISHER AND event_type:SERIALIZATION_ERROR
```

### Failures from Specific Client
```
client_id:"mqtt5-publisher-12345" AND success:false
```

## Performance Considerations

### Elasticsearch Sizing
- **Development**: 1 node, 2 GB RAM
- **Production** (100 clients): 3 nodes, 8 GB RAM each
- **Scale** (1000+ clients): 6+ nodes, 16 GB RAM each

### Index Lifecycle Management (ILM)
Configure automatic index rollover and retention:

```bash
# Create ILM policy (indices rotate daily, keep for 30 days)
curl -X PUT "http://localhost:9200/_ilm/policy/mqtt5-validation-policy" \
  -H 'Content-Type: application/json' \
  -d '{
    "policy": {
      "phases": {
        "hot": {
          "actions": {
            "rollover": {
              "max_age": "1d",
              "max_size": "50GB"
            }
          }
        },
        "delete": {
          "min_age": "30d",
          "actions": {
            "delete": {}
          }
        }
      }
    }
  }'
```

### Log Retention
- **Hot data**: 7 days (fast queries)
- **Warm data**: 30 days (slower queries)
- **Cold storage**: Archive to S3 for compliance

## Troubleshooting

### No Data in Kibana
1. Check if logs are being written:
   ```bash
   tail -f /var/log/mqtt5/publisher.log | grep VALIDATION_EVENT
   ```

2. Verify Filebeat is running:
   ```bash
   docker-compose logs filebeat
   ```

3. Check Elasticsearch indices:
   ```bash
   curl http://localhost:9200/_cat/indices?v | grep mqtt5
   ```

### High Log Volume
1. Increase sampling rate for successful validations
2. Enable log compression in Filebeat
3. Adjust ILM policy to delete old data sooner

### Missing Fields in Dashboard
Refresh the index pattern in Kibana:
1. Go to **Management** > **Index Patterns**
2. Select `mqtt5-validation-*`
3. Click the **Refresh** button

## Integration with Existing Monitoring

### Prometheus + Grafana
Export metrics from Elasticsearch to Prometheus:

```yaml
# Add to docker-compose.yml
elasticsearch-exporter:
  image: quay.io/prometheuscommunity/elasticsearch-exporter:latest
  command:
    - '--es.uri=http://elasticsearch:9200'
  ports:
    - "9114:9114"
```

### Datadog
Use Datadog's Elasticsearch integration to forward metrics.

### Splunk
Use the Splunk HTTP Event Collector (HEC) as an alternative output in Filebeat.

## Cost Optimization

### Elastic Cloud Pricing
- **Basic**: Free tier available
- **Standard**: ~$95/month (3 nodes, 8GB RAM)
- **Enterprise**: ~$300/month (includes ML, security)

### AWS OpenSearch
- **Development**: ~$50/month (t3.small.search)
- **Production**: ~$200/month (r6g.large.search x 3)

### Self-Hosted
- **Development**: Free (Docker on laptop)
- **Production**: EKS costs only (~$150/month for monitoring namespace)

## Next Steps

1. **Enable authentication**: Configure X-Pack security or use AWS IAM
2. **Set up SSL/TLS**: Encrypt data in transit
3. **Configure backup**: Snapshot Elasticsearch to S3
4. **Create custom visualizations**: Build dashboards specific to your use case
5. **Integrate with incident management**: Connect alerts to your ticketing system

## Support

For issues with:
- **ELK Stack**: https://discuss.elastic.co
- **Schema Registry**: Solace Community (https://solace.community)
- **This Integration**: Open an issue in the GitHub repository

