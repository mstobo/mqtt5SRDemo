#!/bin/bash
# Quick start script for ELK stack integration with MQTT5 Schema Validation

set -e

echo "================================================================"
echo "ELK Stack Setup for MQTT5 Schema Validation Monitoring"
echo "================================================================"
echo

# Check prerequisites
echo "Checking prerequisites..."
command -v docker >/dev/null 2>&1 || { echo "Error: docker is required but not installed."; exit 1; }
command -v docker-compose >/dev/null 2>&1 || { echo "Error: docker-compose is required but not installed."; exit 1; }
echo "Prerequisites OK"
echo

# Create log directory
echo "Creating log directory..."
sudo mkdir -p /var/log/mqtt5
sudo chmod 777 /var/log/mqtt5
echo "Log directory created: /var/log/mqtt5"
echo

# Start ELK stack
echo "Starting ELK stack (this may take 2-3 minutes)..."
cd "$(dirname "$0")"
docker-compose up -d

echo "Waiting for services to become healthy..."
sleep 30

# Wait for Elasticsearch
echo "Waiting for Elasticsearch..."
until curl -s http://localhost:9200/_cluster/health >/dev/null 2>&1; do
    echo "  Elasticsearch not ready yet, waiting..."
    sleep 10
done
echo "Elasticsearch is ready!"

# Wait for Kibana
echo "Waiting for Kibana..."
until curl -s http://localhost:5601/api/status >/dev/null 2>&1; do
    echo "  Kibana not ready yet, waiting..."
    sleep 10
done
echo "Kibana is ready!"

# Create index template
echo
echo "Creating Elasticsearch index template..."
curl -s -X PUT "http://localhost:9200/_index_template/mqtt5-validation-template" \
  -H 'Content-Type: application/json' \
  -d @elasticsearch-mapping.json > /dev/null
echo "Index template created"

# Create index pattern in Kibana (requires API call after Kibana is fully ready)
echo
echo "Waiting for Kibana to be fully initialized..."
sleep 15

echo "Creating Kibana index pattern..."
curl -s -X POST "http://localhost:5601/api/data_views/data_view" \
  -H 'Content-Type: application/json' \
  -H 'kbn-xsrf: true' \
  -d '{
    "data_view": {
      "title": "mqtt5-validation-*",
      "name": "MQTT5 Validation Events",
      "timeFieldName": "@timestamp"
    }
  }' > /dev/null || echo "  (Index pattern may already exist or will be created on first data)"

echo
echo "================================================================"
echo "ELK Stack is ready!"
echo "================================================================"
echo
echo "Services:"
echo "  Elasticsearch: http://localhost:9200"
echo "  Kibana:        http://localhost:5601"
echo "  Logstash:      localhost:5044 (Beats input)"
echo
echo "Next steps:"
echo "  1. Compile your MQTT5 application:"
echo "     cd .. && mvn clean compile"
echo
echo "  2. Run Publisher with logging:"
echo "     mvn exec:java -Dexec.mainClass=\"MQTT5Publisher\" > /var/log/mqtt5/publisher.log 2>&1 &"
echo
echo "  3. Run Subscriber with logging:"
echo "     mvn exec:java -Dexec.mainClass=\"MQTT5Subscriber\" > /var/log/mqtt5/subscriber.log 2>&1 &"
echo
echo "  4. View logs being collected:"
echo "     tail -f /var/log/mqtt5/*.log | grep VALIDATION_EVENT"
echo
echo "  5. Open Kibana and create visualizations:"
echo "     http://localhost:5601"
echo
echo "  6. Import the pre-built dashboard:"
echo "     Management > Stack Management > Saved Objects > Import"
echo "     Then upload: elk/kibana-dashboard.json"
echo
echo "To stop the ELK stack:"
echo "  docker-compose down"
echo
echo "To stop and remove all data:"
echo "  docker-compose down -v"
echo
echo "================================================================"

