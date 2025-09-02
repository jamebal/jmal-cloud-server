#!/bin/bash

# 初始化文件
rm -rf /Users/jmal/temp/filetest/rootpath-test/

# 初始化MongoDB数据库
mongosh --eval "db.getSiblingDB('jmalcloud-test').dropDatabase()"

JAR_NAME=$(ls target/jmalcloud-*.jar | sort -V | tail -n 1)

java -agentpath:${GRAALVM_HOME}/lib/libnative-image-agent.dylib=config-output-dir=src/main/resources/META-INF/native-image -jar "$JAR_NAME" \
 -Duser.timezone=${TZ} \
 -Dfile.encoding=UTF-8 \
 --server.port=8099 \
 --spring.profiles.active="dev, mongodb" \
 --spring.data.mongodb.uri=mongodb://127.0.0.1:27017/jmalcloud-test \
 --file.exactSearch=true \
 --file.rootDir='/Users/jmal/temp/filetest/rootpath-test' \

