#!/bin/bash

JAR_NAME=$(ls target/clouddisk-*.jar | sort -V | tail -n 1)

#java -agentpath:/Users/jmal/.sdkman/candidates/java/current/lib/libnative-image-agent.dylib=config-output-dir=src/main/resources/META-INF/native-image -jar "$JAR_NAME"
java -agentpath:/Users/jmal/.sdkman/candidates/java/current/lib/libnative-image-agent.dylib=config-output-dir=src/main/resources/native-image-test -jar "$JAR_NAME"

