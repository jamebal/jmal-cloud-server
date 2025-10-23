#!/bin/bash
set -e

USER_UID=${PUID:-0}
USER_GID=${PGID:-0}
TZ=${TZ:-Asia/Shanghai}

mkdir -p /app/log
chown ${USER_UID}:${USER_GID} /jmalcloud

chown -R ${USER_UID}:${USER_GID} /jmalcloud/models
chown -R ${USER_UID}:${USER_GID} /jmalcloud/tess4j
chown ${USER_UID}:${USER_GID} /jmalcloud/ip2region.xdb
chown -R ${USER_UID}:${USER_GID} /usr/local/mxcad
chown -R ${USER_UID}:${USER_GID} /app

DATA_BASE_TYPE=${DATA_BASE_TYPE:-"mongodb"}
RESET_ADMIN_PASSWORD=${RESET_ADMIN_PASSWORD:-"false"}

case "${DATA_BASE_TYPE}" in
  sqlite)
    RUN_ENVIRONMENT="prod-sqlite"
    ;;
  mysql)
    RUN_ENVIRONMENT="prod-mysql"
    ;;
  pgsql|postgresql)
    RUN_ENVIRONMENT="prod-pgsql"
    ;;
  mongodb)
    if [ -z "${MONGODB_URI}" ]; then
      RUN_ENVIRONMENT="prod-sqlite"
    else
      MONGODB_URI=${MONGODB_URI:-"mongodb://mongo:27017/jmalcloud"}
      RUN_ENVIRONMENT="prod-mongodb"
    fi
    ;;
  *)
    RUN_ENVIRONMENT="prod-sqlite"
    ;;
esac

echo "Starting JMalCloud with environment: ${RUN_ENVIRONMENT}"

exec gosu ${USER_UID}:${USER_GID} /app/jmalcloud ${JVM_OPTS} \
 -Duser.timezone=${TZ} \
 -Dfile.encoding=UTF-8 \
 --spring.profiles.active=${RUN_ENVIRONMENT:-"prod-mongodb"} \
 --jmalcloud.datasource.migration=${MIGRATION:-"false"} \
 --spring.data.mongodb.uri=${MONGODB_URI:-""} \
 --tess4j.data-path=${TESS4J_DATA_PATH} \
 --file.frontendResourcePath=/app/frontend/ \
 --file.pdfjsResourcePath=/webapp/plug/pdfjs/ \
 --file.drawioResourcePath=/webapp/plug/draw/ \
 --file.excalidrawResourcePath=/webapp/plug/excalidraw/excalidraw/app/ \
 --file.exactSearch=${EXACT_SEARCH} \
 --file.ngramMaxContentLengthMB=${NGRAM_MAX_CONTENT_LENGTH_MB} \
 --file.ngramMinSize=${NGRAM_MIN_SIZE} \
 --file.ngramMaxSize=${NGRAM_MAX_SIZE} \
 --file.monitor=${FILE_MONITOR} \
 --file.rootDir=${FILE_ROOT_DIR} \
 --file.resetAdminPassword=${RESET_ADMIN_PASSWORD} \
 --file.monitorIgnoreFilePrefix=${MONITOR_IGNORE_FILE_PREFIX} \
 --logging.level.root=${LOG_LEVEL} \
 --file.ip2region-db-path=/jmalcloud/ip2region.xdb \
 --file.ocr-lite-onnx-model-path=/jmalcloud/models
