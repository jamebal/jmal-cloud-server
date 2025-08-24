FROM ghcr.io/jamebal/jmalcloud_mid:latest

ARG VERSION

ENV MONGODB_URI "mongodb://mongo:27017/jmalcloud"
ENV RUN_ENVIRONMENT prod
ENV JVM_OPTS ""
ENV LOG_LEVEL warn

# 是否开启精确搜索
ENV EXACT_SEARCH false
ENV NGRAM_MAX_CONTENT_LENGTH_MB "5"
ENV NGRAM_MIN_SIZE "2"
ENV NGRAM_MAX_SIZE "6"

ENV FILE_MONITOR true
ENV MONITOR_IGNORE_FILE_PREFIX ".DS_Store,._"
ENV FILE_ROOT_DIR /jmalcloud/files
ENV TESS4J_DATA_PATH /jmalcloud/tess4j/datapath

# 从构建器阶段复制编译好的可执行文件
# Spring Boot GraalVM 插件默认会将可执行文件放在 target 目录
COPY docker-entrypoint.sh target/jmalcloud frontend/frontend_dist target/*.so /app/

RUN chmod +x /app/jmalcloud && chmod +x /app/docker-entrypoint.sh

VOLUME /jmalcloud/

EXPOSE 8088

# FTP Server
EXPOSE 8089

# HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 CMD curl -f http://localhost:8088/public/health > /dev/null || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
