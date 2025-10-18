import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;


public class MQTT5Publisher {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private MqttAsyncClient client;
    private final String clientId;
    
    public MQTT5Publisher() {
        this.clientId = MqttConfig.generateClientId("mqtt5-publisher");
    }
    
    public static void main(String[] args) {
        // Print configuration at startup
        MqttConfig.printConfiguration();
        
        // Check if configuration is valid
        if (!MqttConfig.isConfigurationValid()) {
            System.err.println("Configuration not set. Please update MqttConfig.java");
            System.err.println("   Update USERNAME, PASSWORD, and BROKER_URL");
            System.exit(1);
        }
        
        MQTT5Publisher publisher = new MQTT5Publisher();
        try {
            publisher.connect();
            publisher.publishMessages();
            publisher.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void connect() throws MqttException, InterruptedException {
        System.out.println("Connecting to MQTT5 broker: " + MqttConfig.BROKER_URL);
        System.out.println("Using username: " + MqttConfig.USERNAME);
        System.out.println("Client ID: " + clientId);
        
        // Create MQTT5 client
        client = new MqttAsyncClient(MqttConfig.BROKER_URL, clientId, new MemoryPersistence());
        
        // Set up connection options for MQTT5
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true); // MQTT5 uses cleanStart instead of cleanSession
        options.setKeepAliveInterval(MqttConfig.KEEP_ALIVE_INTERVAL);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(MqttConfig.CONNECTION_TIMEOUT);
        
        // Set authentication credentials from config
        options.setUserName(MqttConfig.USERNAME);
        options.setPassword(MqttConfig.PASSWORD.getBytes());
        
        // Set callback for connection events
        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                System.out.println("Disconnected: " + disconnectResponse.getReasonString());
            }
            
            @Override
            public void mqttErrorOccurred(MqttException exception) {
                System.err.println("MQTT Error: " + exception.getMessage());
            }
            
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // Not used in publisher, but required by interface
            }
            
            @Override
            public void deliveryComplete(IMqttToken token) {
                System.out.println("Message delivery completed");
            }
            
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("Connection completed to: " + serverURI + 
                                 (reconnect ? " (reconnected)" : " (initial connection)"));
            }
            
            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
                System.out.println("Auth packet received with reason code: " + reasonCode);
            }
        });
        
        // Connect synchronously for simplicity
        try {
            client.connect(options);
            System.out.println("Successfully connected to MQTT5 broker with authentication");
        } catch (MqttException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            if (e.getMessage().contains("Not authorized")) {
                System.err.println("   Authentication failed - check username/password in MqttConfig.java");
            }
            throw e;
        }
    }
    
    public void publishMessages() throws MqttException, InterruptedException {
        System.out.println("Publishing messages to topic: " + MqttConfig.TOPIC_BASE);
        
        boolean useSerdes = MqttConfig.JSON_SERDES_ENABLED && MqttConfig.JSON_PUBLISH_WITH_SERDES;
        com.solace.serdes.jsonschema.JsonSchemaSerializer<JsonNode> serializer = null;
        if (useSerdes) {
            serializer = SerdesSupport.getJsonSerializer();
        }
        
        for (int i = 1; i <= 20; i++) {
            // Every 3rd message: send invalid payload to test schema validation
            boolean sendInvalid = (i % 3 == 0);
            
            String name = "User " + i;
            String id = String.valueOf(i);
            String email = "user" + i + "@example.com";
            
            byte[] outBytes;
            java.util.Map<String, Object> serdesHeaders = new java.util.HashMap<>();
            if (useSerdes) {
                try {
                    JsonNode jsonNode;
                    if (sendInvalid) {
                        // Create invalid payload (missing required fields or wrong types)
                        System.out.println("Attempting to send INVALID message " + i + " (missing required fields)");
                        jsonNode = JSON.createObjectNode();
                        ((ObjectNode) jsonNode).put("invalid_field", "This message doesn't match the schema");
                        ((ObjectNode) jsonNode).put("another_bad_field", i);
                    } else {
                        jsonNode = SerdesSupport.buildUserJson(name, id, email);
                    }
                    
                    // Pre-populate SCHEMA_ID_STRING with the artifact ID for deserializer
                    serdesHeaders.put("SCHEMA_ID_STRING", MqttConfig.SCHEMA_ARTIFACT_ID);
                    
                    // Use artifact ID as the "topic" key for schema resolution
                    outBytes = serializer.serialize(MqttConfig.SCHEMA_ARTIFACT_ID, jsonNode, serdesHeaders);
                    
                    if (sendInvalid) {
                        System.err.println("WARNING: Invalid message " + i + " was NOT rejected by schema validation!");
                    }
                } catch (Exception e) {
                    if (sendInvalid) {
                        System.out.println("SUCCESS: Invalid message " + i + " was rejected by schema validation");
                        System.out.println("  Reason: " + e.getMessage());
                    } else {
                        System.err.println("ERROR: Valid message " + i + " failed serialization: " + e.getMessage());
                        e.printStackTrace();
                    }
                    continue;
                }
            } else {
                // Plain JSON publish path (no serializer)
                String json = SerdesSupport.jsonToString(SerdesSupport.buildUserJson(name, id, email));
                outBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                serdesHeaders.put("SCHEMA_ID_STRING", MqttConfig.SCHEMA_ARTIFACT_ID);
            }
            
            MqttMessage message = new MqttMessage(outBytes);
            message.setQos(MqttConfig.PUBLISH_QOS);
            message.setRetained(false);
            
            MqttProperties messageProperties = new MqttProperties();
            messageProperties.setMessageExpiryInterval(MqttConfig.MESSAGE_EXPIRY_INTERVAL);
            messageProperties.setPayloadFormat(true); // UTF-8 JSON
            messageProperties.setContentType("application/json");
            messageProperties.setCorrelationData(("correlation-" + i).getBytes());
            messageProperties.setResponseTopic(MqttConfig.RESPONSE_TOPIC);
            
            // Add user properties
            messageProperties.getUserProperties().add(new org.eclipse.paho.mqttv5.common.packet.UserProperty("messageId", String.valueOf(i)));
            messageProperties.getUserProperties().add(new org.eclipse.paho.mqttv5.common.packet.UserProperty("sender", "MQTT5Publisher"));
            messageProperties.getUserProperties().add(new org.eclipse.paho.mqttv5.common.packet.UserProperty("username", MqttConfig.USERNAME));
            messageProperties.getUserProperties().add(new org.eclipse.paho.mqttv5.common.packet.UserProperty("clientId", clientId));
            
            // Add SERDES headers populated by the serializer (includes SCHEMA_ID_STRING)
            SerdesSupport.addSerdesHeadersToUserProps(serdesHeaders, messageProperties.getUserProperties());
            
            // If serializer didn't add SCHEMA_ID_STRING (e.g., non-SERDES path), add it manually
            if (!serdesHeaders.containsKey("SCHEMA_ID_STRING")) {
                messageProperties.getUserProperties().add(new org.eclipse.paho.mqttv5.common.packet.UserProperty("SCHEMA_ID_STRING", MqttConfig.SCHEMA_ARTIFACT_ID));
            }
            
            message.setProperties(messageProperties);
            
            try {
                client.publish(MqttConfig.TOPIC_BASE, message);
                System.out.println("Published user record: {name=" + name + ", id=" + id + ", email=" + email + "}");
            } catch (MqttException e) {
                System.err.println("Failed to publish message: " + e.getMessage());
            }
            
            Thread.sleep(1000);
        }
        
        System.out.println("Finished publishing all messages");
    }
    
    public void disconnect() throws MqttException, InterruptedException {
        System.out.println("Disconnecting from MQTT5 broker...");
        
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                System.out.println("Successfully disconnected");
            } catch (MqttException e) {
                System.err.println("Disconnect failed: " + e.getMessage());
            } finally {
                client.close();
            }
        }
    }
}
