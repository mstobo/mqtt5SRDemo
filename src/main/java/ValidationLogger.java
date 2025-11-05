import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Structured logger for emitting validation events in JSON format
 * for ingestion by ELK stack (Elasticsearch, Logstash, Kibana).
 * 
 * These logs provide operational visibility into message quality
 * and schema validation failures across distributed MQTT5 clients.
 */
public class ValidationLogger {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    
    // Event types for filtering and aggregation in Kibana
    public enum EventType {
        VALIDATION_SUCCESS,
        VALIDATION_FAILURE,
        SERIALIZATION_ERROR,
        DESERIALIZATION_ERROR,
        PUBLISH_ERROR,
        MESSAGE_RECEIVED
    }
    
    // Client types
    public enum ClientType {
        PUBLISHER,
        SUBSCRIBER
    }
    
    /**
     * Log a validation event in structured JSON format
     * This goes to stdout and can be collected by Filebeat/Fluentd
     */
    public static void logValidationEvent(
            EventType eventType,
            ClientType clientType,
            String messageId,
            String schemaId,
            String topic,
            boolean success,
            String errorMessage,
            String clientId,
            String brokerUrl,
            String sensorId,
            Double temperature) {
        
        try {
            ObjectNode event = JSON.createObjectNode();
            
            // Standard fields
            event.put("@timestamp", ISO_FORMATTER.format(Instant.now()));
            event.put("event_type", eventType.name());
            event.put("client_type", clientType.name());
            event.put("success", success);
            event.put("message_id", messageId);
            event.put("schema_id", schemaId);
            event.put("topic", topic);
            event.put("client_id", clientId);
            event.put("broker_url", brokerUrl);
            
            // Application-specific fields
            event.put("application", "mqtt5-schema-validation");
            event.put("environment", System.getenv().getOrDefault("ENV", "development"));
            event.put("hostname", getHostname());
            
            // Business data fields (for correlation)
            if (sensorId != null) {
                event.put("sensor_id", sensorId);
            }
            if (temperature != null) {
                event.put("temperature", temperature);
            }
            
            // Error details (if failure)
            if (!success && errorMessage != null) {
                event.put("error_message", errorMessage);
                event.put("error_category", categorizeError(errorMessage));
            }
            
            // Output as single-line JSON (required for log shippers)
            System.out.println("[VALIDATION_EVENT] " + JSON.writeValueAsString(event));
            
        } catch (Exception e) {
            System.err.println("Failed to log validation event: " + e.getMessage());
        }
    }
    
    /**
     * Convenience method for publisher validation failures
     */
    public static void logPublisherValidationFailure(
            String messageId,
            String schemaId,
            String topic,
            String errorMessage,
            String clientId,
            String sensorId,
            Double temperature) {
        
        logValidationEvent(
            EventType.VALIDATION_FAILURE,
            ClientType.PUBLISHER,
            messageId,
            schemaId,
            topic,
            false,
            errorMessage,
            clientId,
            MqttConfig.BROKER_URL,
            sensorId,
            temperature
        );
    }
    
    /**
     * Convenience method for subscriber validation failures
     */
    public static void logSubscriberValidationFailure(
            String messageId,
            String schemaId,
            String topic,
            String errorMessage,
            String clientId,
            String sensorId) {
        
        logValidationEvent(
            EventType.DESERIALIZATION_ERROR,
            ClientType.SUBSCRIBER,
            messageId,
            schemaId,
            topic,
            false,
            errorMessage,
            clientId,
            MqttConfig.BROKER_URL,
            sensorId,
            null
        );
    }
    
    /**
     * Convenience method for successful validation (sample a percentage)
     */
    public static void logSuccessfulValidation(
            ClientType clientType,
            String messageId,
            String schemaId,
            String topic,
            String clientId,
            String sensorId) {
        
        // Sample only 5% of successful validations to reduce log volume
        if (Math.random() < 0.05) {
            logValidationEvent(
                EventType.VALIDATION_SUCCESS,
                clientType,
                messageId,
                schemaId,
                topic,
                true,
                null,
                clientId,
                MqttConfig.BROKER_URL,
                sensorId,
                null
            );
        }
    }
    
    /**
     * Categorize errors for better filtering in Kibana
     */
    private static String categorizeError(String errorMessage) {
        if (errorMessage == null) return "unknown";
        
        String lowerMsg = errorMessage.toLowerCase();
        if (lowerMsg.contains("required property") || lowerMsg.contains("not found")) {
            return "missing_field";
        } else if (lowerMsg.contains("maximum") || lowerMsg.contains("minimum")) {
            return "value_out_of_range";
        } else if (lowerMsg.contains("type")) {
            return "type_mismatch";
        } else if (lowerMsg.contains("failed to resolve schema")) {
            return "schema_not_found";
        } else if (lowerMsg.contains("connection") || lowerMsg.contains("timeout")) {
            return "connectivity_issue";
        } else {
            return "validation_error";
        }
    }
    
    /**
     * Get hostname for identifying the source of logs
     */
    private static String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}

