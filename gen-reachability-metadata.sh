#!/bin/bash
# 生成所有依赖库的路径
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
# 读取到环境变量中
CP=$(cat cp.txt)
# 构造包含我们自己代码的完整 Classpath
FULL_CP="$CP:target/classes"

java \
  -agentpath:/Users/jmal/.sdkman/candidates/java/current/lib/libnative-image-agent.dylib=config-output-dir=src/main/resources/META-INF/native-image \
  -cp "$FULL_CP" \
  com.jmal.clouddisk.JmalCloudApplication
