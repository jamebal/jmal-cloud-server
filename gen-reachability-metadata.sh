#!/bin/bash

JAR_NAME=$(ls target/jmalcloud-*.jar | sort -V | tail -n 1)

java -agentpath:${GRAALVM_HOME}/lib/libnative-image-agent.dylib=config-output-dir=src/main/resources/META-INF/native-image -jar "$JAR_NAME"

