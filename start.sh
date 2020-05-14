#!/bin/bash
nohup java -Xms50m -Xmx512m -jar clouddisk-0.0.1-SNAPSHOT-exec.jar --file.rootDir=$1 2>&1 &
echo "文件目录$1"
