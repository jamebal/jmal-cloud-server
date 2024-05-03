FROM jmal/jdk17_ffmpeg_nvidia:latest

ENV MONGODB_URI "mongodb://mongo:27017/jmalcloud"
ENV RUN_ENVIRONMENT=prod

ARG VERSION

ADD target/clouddisk-${VERSION}.jar /usr/local/

VOLUME /jmalcloud/

# 设置支持的平台
ARG TARGETPLATFORM
RUN echo "Building for platform: $TARGETPLATFORM"
LABEL org.label-schema.build.multi-platform=true
ENV PLATFORM=$TARGETPLATFORM
ENV VERSION=${VERSION}

# 将 Linux/arm64/v8 架构设置为默认平台
# 如果需要，可以根据需要更改此设置
ENV DOCKER_DEFAULT_PLATFORM=linux/amd64,linux/arm64

EXPOSE 8088

CMD java -Dloader.path=/usr/local/clouddisk-lib -jar -Xms50m -Xmx512m /usr/local/clouddisk-$VERSION.jar --spring.profiles.active=$RUN_ENVIRONMENT --spring.data.mongodb.uri=$MONGODB_URI --file.monitor=false --file.rootDir=/jmalcloud/files --file.ip2region-db-path=/jmalcloud/ip2region.xdb
