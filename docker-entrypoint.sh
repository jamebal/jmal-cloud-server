#!/bin/bash

nohup /usr/local/tomcat/bin/catalina.sh run > /var/log/tomcat.log 2>&1 &

java -Dfile.encoding=UTF-8 -Dloader.path=/usr/local/clouddisk-lib -jar -Xms50m -Xmx512m /usr/local/clouddisk-${VERSION}.jar --spring.profiles.active=${RUN_ENVIRONMENT} --spring.data.mongodb.uri=${MONGODB_URI} --file.monitor=${FILE_MONITOR} --file.rootDir=${FILE_ROOT_DIR} --logging.level.root=${LOG_LEVEL} --file.ip2region-db-path=/jmalcloud/ip2region.xdb

