FROM ghcr.io/jamebal/jmalcloud_mid:latest

ARG VERSION

ENV MONGODB_URI "mongodb://mongo:27017/jmalcloud"
ENV RUN_ENVIRONMENT prod
ENV JVM_OPTS ""
ENV LOG_LEVEL warn

ENV EXACT_SEARCH false
ENV NGRAM_MAX_CONTENT_LENGTH_MB "5"
ENV NGRAM_MIN_SIZE "2"
ENV NGRAM_MAX_SIZE "6"

ENV FILE_MONITOR true
ENV MONITOR_IGNORE_FILE_PREFIX ".DS_Store,._"
ENV FILE_ROOT_DIR /jmalcloud/files
ENV TESS4J_DATA_PATH /jmalcloud/tess4j/datapath

COPY docker-entrypoint.sh target/jmalcloud target/*.so /app/
COPY frontend/frontend /app/

RUN chmod +x /app/jmalcloud && chmod +x /app/docker-entrypoint.sh

VOLUME /jmalcloud/

EXPOSE 8088

ENTRYPOINT ["/app/docker-entrypoint.sh"]
