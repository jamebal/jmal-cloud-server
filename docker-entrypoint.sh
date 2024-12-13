#!/bin/bash
set -e

USER_UID=${PUID:-0}
USER_GID=${PGID:-0}

mkdir -p /log

chown -R ${USER_UID}:${USER_GID} /jmalcloud

chown ${USER_UID}:${USER_GID} /usr/local/clouddisk-${VERSION}.jar
chown -R ${USER_UID}:${USER_GID} /usr/local/clouddisk-lib
chown -R ${USER_UID}:${USER_GID} log

exec gosu ${USER_UID}:${USER_GID} java -Dfile.encoding=UTF-8 -Dloader.path=/usr/local/clouddisk-lib -jar ${JVM_OPTS} /usr/local/clouddisk-${VERSION}.jar --spring.profiles.active=${RUN_ENVIRONMENT} --spring.data.mongodb.uri=${MONGODB_URI} --tess4j.data-path=${TESS4J_DATA_PATH} --file.monitor=${FILE_MONITOR} --file.rootDir=${FILE_ROOT_DIR} --file.monitorIgnoreFilePrefix=${MONITOR_IGNORE_FILE_PREFIX} --logging.level.root=${LOG_LEVEL} --file.ip2region-db-path=/jmalcloud/ip2region.xdb --file.ocr-lite-onnx-model-path=/jmalcloud/models
