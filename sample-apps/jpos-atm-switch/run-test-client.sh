#!/bin/bash

# Run the test client
APP_HOME="build/install/jpos-atm-switch"

if [ ! -d "$APP_HOME" ]; then
    echo "Application not installed. Running './gradlew installApp'..."
    ./gradlew installApp
fi

# Build classpath with all jars
CLASSPATH=""
for jar in $APP_HOME/lib/*.jar; do
    if [ -z "$CLASSPATH" ]; then
        CLASSPATH="$jar"
    else
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

echo "Starting Test Client..."
java -cp "$CLASSPATH" com.example.atm.TestClient
