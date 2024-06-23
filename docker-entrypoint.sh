#!/bin/bash
set -e

USER_UID=${PUID:-0}
USER_GID=${PGID:-0}

# 创建用户组和用户，并将其 UID 和 GID 设置为环境变量指定的值
if ! getent group appgroup > /dev/null 2>&1; then
    groupadd -g $USER_GID appgroup || true
fi

if ! id -u appuser > /dev/null 2>&1; then
    useradd -m -u $USER_UID -g appgroup appuser || true
fi

# 更改挂载路径的所有者
chown -R ${USER_UID}:${USER_GID} /jmalcloud

# 启动主应用程序
exec gosu ${USER_UID}:${USER_GID} java -Dfile.encoding=UTF-8 -Dloader.path=/usr/local/clouddisk-lib -jar ${JVM_OPTS} /usr/local/clouddisk-${VERSION}.jar --spring.profiles.active=${RUN_ENVIRONMENT} --spring.data.mongodb.uri=${MONGODB_URI} --tess4j.data-path=${TESS4J_DATA_PATH} --file.monitor=${FILE_MONITOR} --file.rootDir=${FILE_ROOT_DIR} --logging.level.root=${LOG_LEVEL} --file.ip2region-db-path=/jmalcloud/ip2region.xdb
