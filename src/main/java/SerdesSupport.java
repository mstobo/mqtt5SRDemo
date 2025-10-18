import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import com.solace.serdes.common.resolver.config.SchemaResolverProperties;
import com.solace.serdes.common.SerdeProperties;
import com.solace.serdes.common.SchemaHeaderId;
import com.solace.serdes.jsonschema.JsonSchemaProperties;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal helper for integrating Solace JSON SERDES via configuration.
 * Uses direct API types; assumes SERDES dependencies are on classpath.
 */
public final class SerdesSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static volatile com.solace.serdes.jsonschema.JsonSchemaSerializer<JsonNode> jsonSerializer;
    private static volatile com.solace.serdes.jsonschema.JsonSchemaDeserializer<JsonNode> jsonDeserializer;

    private SerdesSupport() {}

    public static synchronized com.solace.serdes.jsonschema.JsonSchemaSerializer<JsonNode> getJsonSerializer() {
        if (jsonSerializer == null) {
            Map<String, Object> config = buildCommonConfig(true);  // Enable validation
            com.solace.serdes.jsonschema.JsonSchemaSerializer<JsonNode> serializer =
                    new com.solace.serdes.jsonschema.JsonSchemaSerializer<>();
            serializer.configure(config);
            jsonSerializer = serializer;
        }
        return jsonSerializer;
    }

    public static synchronized com.solace.serdes.jsonschema.JsonSchemaDeserializer<JsonNode> getJsonDeserializer() {
        if (jsonDeserializer == null) {
            Map<String, Object> config = buildCommonConfig(true);  // Enable validation
            com.solace.serdes.jsonschema.JsonSchemaDeserializer<JsonNode> deserializer =
                    new com.solace.serdes.jsonschema.JsonSchemaDeserializer<>();
            deserializer.configure(config);
            jsonDeserializer = deserializer;
        }
        return jsonDeserializer;
    }

    public static Map<String, Object> buildCommonConfig(boolean enableValidation) {
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaResolverProperties.REGISTRY_URL, MqttConfig.SCHEMA_REGISTRY_URL);
        config.put(SchemaResolverProperties.AUTH_USERNAME, MqttConfig.SCHEMA_REGISTRY_USERNAME);
        config.put(SchemaResolverProperties.AUTH_PASSWORD, MqttConfig.SCHEMA_REGISTRY_PASSWORD);
        config.put(JsonSchemaProperties.VALIDATE_SCHEMA, enableValidation && MqttConfig.JSON_VALIDATE_SCHEMA);
        // Ensure string-based schema identifiers for cross-protocol compatibility
        config.put(SerdeProperties.SCHEMA_HEADER_IDENTIFIERS, SchemaHeaderId.SCHEMA_ID_STRING);
        return config;
    }

    public static ObjectNode buildSampleJson(String payloadText, int messageIndex) {
        ObjectNode node = JSON.createObjectNode();
        node.put("message", payloadText);
        node.put("index", messageIndex);
        node.put("ts", System.currentTimeMillis());
        return node;
    }

    public static ObjectNode buildUserJson(String name, String id, String email) {
        ObjectNode node = JSON.createObjectNode();
        node.put("name", name);
        node.put("id", id);
        node.put("email", email);
        return node;
    }

    public static Map<String, Object> mqttUserPropsToSerdesHeaders(List<UserProperty> userProperties) {
        Map<String, Object> headers = new HashMap<>();
        if (userProperties == null) {
            return headers;
        }
        for (UserProperty up : userProperties) {
            headers.put(up.getKey(), up.getValue());
        }
        return headers;
    }

    public static void addSerdesHeadersToUserProps(Map<String, Object> headers, List<UserProperty> userProperties) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            Object value = e.getValue();
            String valueString = value == null ? "" : value.toString();
            userProperties.add(new UserProperty(e.getKey(), valueString));
        }
    }

    public static String jsonToString(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            return new String("<unprintable json>".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
    }
}


