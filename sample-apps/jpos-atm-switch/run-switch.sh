#!/bin/bash

APP_HOME="build/install/jpos-atm-switch"

if [ ! -d "$APP_HOME" ]; then
    echo "Application not installed. Running './gradlew installApp'..."
    ./gradlew installApp
fi

# Build classpath
CLASSPATH=""
for jar in $APP_HOME/lib/*.jar; do
    if [ -z "$CLASSPATH" ]; then
        CLASSPATH="$jar"
    else
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

echo "Starting jPOS Q2..."
echo "Deploy directory: $APP_HOME/deploy"
echo ""

cd $APP_HOME
java -cp "$CLASSPATH" org.jpos.q2.Q2
