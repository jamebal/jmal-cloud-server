#!/bin/bash

mongosh --eval "db.getSiblingDB('jmalcloud-test').dropDatabase()"

rm -rf /Users/jmal/temp/filetest/rootpath-test/luceneIndex

JAR_NAME=$(ls target/jmalcloud-*.jar | sort -V | tail -n 1)

java -agentpath:${GRAALVM_HOME}/lib/libnative-image-agent.dylib=config-output-dir=src/main/resources/META-INF/native-image -jar "$JAR_NAME" \
 --spring.profiles.active='dev, mongodb' \
 --server.port=8099 \
 -Dfile.encoding=UTF-8 \
 --spring.data.mongodb.uri=mongodb://127.0.0.1:27017/jmalcloud-test \
 --file.exactSearch=true \
 --file.rootDir=/Users/jmal/temp/filetest/rootpath-test
