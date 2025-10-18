#!/bin/bash

echo "Setting up Maven project structure for MQTT5 examples..."

# Create Maven directory structure
mkdir -p src/main/java
mkdir -p src/main/resources
mkdir -p src/test/java

# Move Java files to proper Maven location
if [ -f "MQTT5Publisher.java" ]; then
    mv MQTT5Publisher.java src/main/java/
    echo "Moved MQTT5Publisher.java to src/main/java/"
fi

if [ -f "MQTT5Subscriber.java" ]; then
    mv MQTT5Subscriber.java src/main/java/
    echo "Moved MQTT5Subscriber.java to src/main/java/"
fi

if [ -f "MqttConfig.java" ]; then
    mv MqttConfig.java src/main/java/
    echo "Moved MqttConfig.java to src/main/java/"
fi

echo ""
echo "Maven project structure created:"
tree src/ 2>/dev/null || find src/ -type f

echo ""
echo "Now you can compile with:"
echo "   mvn clean compile"
echo ""
echo "Run the examples with:"
echo "   mvn exec:java -Dexec.mainClass=\"MQTT5Subscriber\""
echo "   mvn exec:java -Dexec.mainClass=\"MQTT5Publisher\""
echo ""
echo "Project structure should look like:"
echo "   ├── pom.xml"
echo "   ├── src/"
echo "   │   └── main/"
echo "   │       └── java/"
echo "   │           ├── MQTT5Publisher.java"
echo "   │           ├── MQTT5Subscriber.java"
echo "   │           └── MqttConfig.java"
echo "   └── README.md"
