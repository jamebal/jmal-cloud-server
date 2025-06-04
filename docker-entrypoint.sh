#!/bin/bash
set -e

USER_UID=${PUID:-0}
USER_GID=${PGID:-0}
JVM_OPTS="${JVM_OPTS:-}"
FINAL_JVM_OPTS=""

mkdir -p /log

chown -R ${USER_UID}:${USER_GID} /jmalcloud

chown ${USER_UID}:${USER_GID} /usr/local/clouddisk-${VERSION}.jar
chown -R ${USER_UID}:${USER_GID} /usr/local/clouddisk-lib
chown -R ${USER_UID}:${USER_GID} log

# 如果EXACT_SEARCH为true, 则JVM_OPTS为空
if [ "${EXACT_SEARCH}" = "true" ]; then
  FINAL_JVM_OPTS=""
else
  FINAL_JVM_OPTS="${JVM_OPTS}"
fi

echo "Final JVM_OPTS to be used: '${FINAL_JVM_OPTS}'"

exec gosu ${USER_UID}:${USER_GID} java ${FINAL_JVM_OPTS} \
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
