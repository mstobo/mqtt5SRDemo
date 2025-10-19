# MQTT5 + Solace Schema Registry Demo

A comprehensive demonstration of integrating MQTT5 with Solace Schema Registry for schema-validated messaging, deployed on AWS EKS.

## Overview

This project demonstrates:
- Deploying Solace Schema Registry on AWS EKS with PostgreSQL backend
- Building MQTT5 publisher and subscriber applications in Java
- Implementing end-to-end schema validation using JSON Schema
- Leveraging MQTT5 User Properties for schema metadata propagation
- Achieving data integrity and quality through client-side validation

## Architecture

### Infrastructure

```
Publisher (Java) → MQTT5 Broker (Solace) → Subscriber (Java)
      ↓                                            ↓
Schema Registry (AWS EKS) ←────────────────────────┘
      ↓
PostgreSQL (CloudNativePG)
```

### Message Flow with Validation

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant SR as Schema Registry
    participant Pub as Publisher
    participant Broker as MQTT5 Broker
    participant Sub as Subscriber
    
    Note over Dev,SR: 1. Schema Registration
    Dev->>SR: POST /apis/registry/v3/groups/default/artifacts
    SR->>SR: Validate JSON Schema
    SR-->>Dev: Schema ID: solace/samples/goodschema
    
    Note over Pub,SR: 2. Publisher Initialization
    Pub->>SR: GET /apis/registry/v3/groups/default/artifacts/{id}
    SR-->>Pub: Return JSON Schema
    Pub->>Pub: Initialize SERDES serializer
    
    Note over Pub,Broker: 3. Message Publishing with Validation
    Pub->>Pub: Create JSON payload
    Pub->>Pub: SERDES serialize + validate
    alt Valid Message
        Pub->>Pub: Add SCHEMA_ID_STRING header
        Pub->>Broker: PUBLISH (QoS 0) with User Properties
        Note right of Pub: User Properties:<br/>- SCHEMA_ID_STRING<br/>- messageId<br/>- sender<br/>- clientId
    else Invalid Message
        Pub->>Pub: Reject (validation failed)
        Note right of Pub: Message never sent
    end
    
    Note over Sub,SR: 4. Subscriber Initialization
    Sub->>SR: GET /apis/registry/v3/groups/default/artifacts/{id}
    SR-->>Sub: Return JSON Schema
    Sub->>Sub: Initialize SERDES deserializer
    Sub->>Broker: SUBSCRIBE test/mqtt5/messages (QoS 0)
    
    Note over Broker,Sub: 5. Message Delivery with Validation
    Broker->>Sub: Deliver message with User Properties
    Sub->>Sub: Extract SERDES headers
    Sub->>Sub: SERDES deserialize + validate
    alt Valid Message
        Sub->>Sub: Process validated message
        Note left of Sub: SERDES validation: PASSED
    else Invalid/Corrupted
        Sub->>Sub: Log error, continue
        Note left of Sub: SERDES validation: FAILED
    end
    
    Note over Pub,Sub: End-to-End Data Integrity
```

### Development Workflow

```mermaid
flowchart TD
    A[Start] --> B[Deploy Schema Registry<br/>DEPLOYMENT.md]
    B --> C[Register JSON Schema<br/>via REST API or Web UI]
    C --> D[Configure Application<br/>MqttConfig.java]
    D --> E[Build Project<br/>mvn clean compile]
    E --> F[Start Subscriber<br/>mvn exec:java -Dexec.mainClass=MQTT5Subscriber]
    F --> G[Start Publisher<br/>mvn exec:java -Dexec.mainClass=MQTT5Publisher]
    
    G --> H{Publisher<br/>Validation}
    H -->|Valid| I[Publish to Broker<br/>with SCHEMA_ID_STRING header]
    H -->|Invalid| J[Reject Message<br/>Log error]
    J --> K[Fix Schema or Data]
    K --> G
    
    I --> L[Broker Routes<br/>to Subscriber]
    L --> M{Subscriber<br/>Validation}
    M -->|Valid| N[Process Message<br/>SERDES validation: PASSED]
    M -->|Invalid| O[Log Error<br/>SERDES validation: FAILED]
    
    N --> P[End-to-End<br/>Data Integrity ✓]
    O --> Q[Investigate:<br/>Schema mismatch?<br/>Data corruption?]
    
    style H fill:#ff9999
    style M fill:#ff9999
    style P fill:#99ff99
    style J fill:#ffcc99
    style O fill:#ffcc99
```


## Project Structure

```
mqtt5SRDemo/
├── src/main/java/
│   ├── MQTT5Publisher.java    # Publisher with schema validation
│   ├── MQTT5Subscriber.java   # Subscriber with schema validation
│   ├── MqttConfig.java         # Configuration management
│   └── SerdesSupport.java      # SERDES helper utilities
├── infra/
│   ├── eks-cluster.yaml        # AWS CloudFormation for EKS
│   ├── schema-registry-ecr.yaml # ECR repositories
│   ├── values-override.yaml.example # Helm values template
│   └── scripts/
│       └── upload_image.sh     # Image upload automation
├── DEPLOYMENT.md               # Complete deployment guide for AWS EKS
├── README.md                   # This file
└── pom.xml                     # Maven dependencies
```

## Quick Start

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- AWS CLI configured (if deploying on AWS)
- kubectl
- Helm 3
- Solace PubSub+ broker (Cloud, software, or appliance)
- Access to Solace Schema Registry images

### 1. Deploy Schema Registry

Schema Registry must be deployed separately. You have two options:

**Option A: Deploy on AWS EKS** (Covered in this repo)

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for complete step-by-step instructions covering:
- AWS EKS cluster setup with CloudFormation
- ECR repository creation and image upload
- PostgreSQL deployment with CloudNativePG
- Schema Registry installation with Helm
- NGINX Ingress with TLS configuration
- Troubleshooting and production considerations

**Option B: Deploy on Other Platforms**

The Schema Registry can be deployed on:
- Google Kubernetes Engine (GKE)
- Azure Kubernetes Service (AKS)
- On-premises Kubernetes
- OpenShift

The Helm chart and deployment approach is similar across platforms. Adjust the infrastructure setup (storage classes, load balancers) for your environment.

### 2. Configure the Application

Edit `src/main/java/MqttConfig.java`:

```java
// For Solace Cloud
public static final String BROKER_URL = "ssl://your-broker.messaging.solace.cloud:8883";
public static final String USERNAME = "your-username";
public static final String PASSWORD = "your-password";

// Schema Registry settings
public static final String SCHEMA_REGISTRY_URL = "https://your-schema-registry-url";
public static final String SCHEMA_ARTIFACT_ID = "solace/samples/goodschema";
```

### 3. Register the Schema

Use the Schema Registry Web UI or REST API to register your JSON schema:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "id": { "type": "string" },
    "email": { "type": "string", "format": "email" }
  },
  "required": ["name", "id", "email"]
}
```

Artifact ID: `solace/samples/goodschema`

### 4. Build the Project

```bash
mvn clean compile
```

### 5. Run the Subscriber

In one terminal:

```bash
mvn exec:java -Dexec.mainClass="MQTT5Subscriber"
```

### 6. Run the Publisher

In another terminal:

```bash
mvn exec:java -Dexec.mainClass="MQTT5Publisher"
```

## Key Features

### End-to-End Validation

- **Publisher**: Validates messages against schema before publishing
- **Subscriber**: Validates incoming messages against schema
- **Data Integrity**: Protection from source to destination

### MQTT5 Features Demonstrated

- User Properties for metadata propagation
- Message Expiry Interval
- Content Type and Payload Format indicators
- QoS 0 and QoS 1 messaging
- Clean Start and Session Expiry

### Schema Management

- JSON Schema validation using Solace SERDES
- String-based schema identifiers (artifact IDs)
- Backward compatibility with non-SERDES clients
- Centralized schema registry

## Documentation

### Key Implementation Details

**Publisher Header Pre-population:**
```java
// Pre-populate SCHEMA_ID_STRING for subscriber compatibility
serdesHeaders.put("SCHEMA_ID_STRING", MqttConfig.SCHEMA_ARTIFACT_ID);
outBytes = serializer.serialize(MqttConfig.SCHEMA_ARTIFACT_ID, jsonNode, serdesHeaders);
```

**Subscriber Header Filtering:**
```java
// Extract only SERDES-specific headers
Map<String, Object> serdesHeaders = new HashMap<>();
for (UserProperty prop : userProps) {
    String key = prop.getKey();
    if (key.equals("SCHEMA_ID_STRING") || key.startsWith("solace.schema.")) {
        serdesHeaders.put(key, prop.getValue());
    }
}
```

## Dependencies

Major dependencies (see `pom.xml` for complete list):

- Eclipse Paho MQTT v5 Client: 1.2.5
- Solace Schema Registry SERDES (JSON): 1.0.0
- Jackson Databind: 2.18.1
- SLF4J: 2.0.16

## Testing

The publisher includes built-in validation tests:
- Messages 1, 2, 4, 5, 7, 8, 10, 11, 13, 14, 16, 17, 19, 20: Valid
- Messages 3, 6, 9, 12, 15, 18: Invalid (missing/malformed email)

Expected behavior:
- Valid messages published and validated on both ends
- Invalid messages rejected at publisher with clear error messages

## Configuration Options

### MqttConfig.java

- `BROKER_URL`: MQTT broker endpoint
- `USERNAME`, `PASSWORD`: Authentication credentials
- `TOPIC_BASE`: Base topic for publishing/subscribing
- `PUBLISH_QOS`, `SUBSCRIBE_QOS`: Quality of Service levels
- `SCHEMA_REGISTRY_URL`: Schema Registry endpoint
- `SCHEMA_ARTIFACT_ID`: Schema identifier
- `JSON_SERDES_ENABLED`: Enable/disable validation
- `JSON_PUBLISH_WITH_SERDES`: Enable publisher validation
- `JSON_SUBSCRIBE_WITH_SERDES`: Enable subscriber validation

## Troubleshooting

### SSL/TLS Certificate Issues

If using self-signed certificates in development:

```bash
# Export certificate from browser or get from server
openssl s_client -connect your-registry:443 -showcerts

# Import to Java truststore
sudo keytool -import -alias schema-registry \
  -keystore $JAVA_HOME/lib/security/cacerts \
  -file registry-cert.crt
```

### Connection Issues

- Verify MQTT broker URL and credentials
- Check firewall rules for ports 8883 (MQTTS) and 443 (HTTPS)
- Ensure Schema Registry is accessible from your network

### Schema Not Found

- Verify artifact ID matches exactly (case-sensitive)
- Check schema is registered in the correct group
- Confirm Schema Registry URL is correct

## Production Considerations

Before deploying to production:

1. **Security**
   - Use CA-signed TLS certificates
   - Integrate with enterprise identity provider (LDAP/OIDC)
   - Enable RBAC for Schema Registry
   - Use AWS Secrets Manager for credentials

2. **High Availability**
   - Deploy multiple Schema Registry replicas
   - Use PostgreSQL with 3+ replicas
   - Configure EKS across multiple AZs
   - Set up pod disruption budgets

3. **Monitoring**
   - Enable Prometheus metrics
   - Configure alerting for validation failures
   - Monitor message latency and throughput
   - Track schema registry API performance

4. **Backup & Recovery**
   - Configure PostgreSQL backups
   - Document schema registry restore procedures
   - Test disaster recovery scenarios

## Resources

- [Solace PubSub+ Cloud](https://solace.com/products/platform/cloud/)
- [Solace Schema Registry Documentation](https://docs.solace.com/Cloud/Schema-Registry/schema-registry-overview.htm)
- [Solace Schema Registry Codelab](https://codelabs.solace.dev/codelabs/schema-registry/index.html)
- [Eclipse Paho MQTT5 Documentation](https://www.eclipse.org/paho/files/mqttdoc/MQTTClient/html/index.html)
- [JSON Schema Documentation](https://json-schema.org/)
- [CloudNativePG Documentation](https://cloudnative-pg.io/)

## License

This project is provided as-is for demonstration and educational purposes.

## Contributing

Issues and pull requests are welcome!

## Author

Matt Stobo - [GitHub](https://github.com/mstobo)

