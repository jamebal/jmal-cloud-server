#!/bin/bash
set -e

USER_UID=${PUID:-0}
USER_GID=${PGID:-0}
TZ=${TZ:-Asia/Shanghai}

mkdir -p /log

chown ${USER_UID}:${USER_GID} /usr/local/clouddisk-${VERSION}.jar
chown -R ${USER_UID}:${USER_GID} /usr/local/clouddisk-lib
chown -R ${USER_UID}:${USER_GID} log

exec gosu ${USER_UID}:${USER_GID} java ${JVM_OPTS} \
 -Duser.timezone=${TZ} \
 -Dfile.encoding=UTF-8 \
 -Dloader.path=/usr/local/clouddisk-lib \
 -jar /usr/local/clouddisk-${VERSION}.jar \
 --spring.profiles.active=${RUN_ENVIRONMENT} \
 --spring.data.mongodb.uri=${MONGODB_URI} \
 --tess4j.data-path=${TESS4J_DATA_PATH} \
 --file.exactSearch=${EXACT_SEARCH} \
 --file.monitor=${FILE_MONITOR} \
 --file.rootDir=${FILE_ROOT_DIR} \
 --file.monitorIgnoreFilePrefix=${MONITOR_IGNORE_FILE_PREFIX} \
 --logging.level.root=${LOG_LEVEL} \
 --file.ip2region-db-path=/jmalcloud/ip2region.xdb \
 --file.ocr-lite-onnx-model-path=/jmalcloud/models
