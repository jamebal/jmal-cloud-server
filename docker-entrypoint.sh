#!/bin/bash
set -e

USER_UID=${PUID:-0}

chown -R "${USER_UID}" /jmalcloud

useradd --shell /bin/bash -u "${USER_UID}" -o -c "" -m user
usermod -a -G root user
export HOME=/home/user

# 启动主应用程序
exec gosu "${USER_UID}" java -Dfile.encoding=UTF-8 -Dloader.path=/usr/local/clouddisk-lib -jar ${JVM_OPTS} /usr/local/clouddisk-${VERSION}.jar --spring.profiles.active=${RUN_ENVIRONMENT} --spring.data.mongodb.uri=${MONGODB_URI} --tess4j.data-path=${TESS4J_DATA_PATH} --file.monitor=${FILE_MONITOR} --file.rootDir=${FILE_ROOT_DIR} --logging.level.root=${LOG_LEVEL} --file.ip2region-db-path=/jmalcloud/ip2region.xdb
