FROM eclipse-temurin:17-jre

MAINTAINER zhushilun084@gmail.com

ARG version=1.0.0

ENV VERSION $version

ENV MONGODB_URI "mongodb://mongo:27017/jmalcloud"

# 安装 ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg libavcodec-extra

RUN mkdir -p /jmalcloud/files

ADD ip2region.xdb /jmalcloud/

ADD clouddisk-$VERSION-exec.jar /usr/local/

VOLUME /jmalcloud/

# 设置支持的平台
ARG TARGETPLATFORM
RUN echo "Building for platform: $TARGETPLATFORM"
LABEL org.label-schema.build.multi-platform=true
ENV PLATFORM=$TARGETPLATFORM

# 将 Linux/arm64/v8 架构设置为默认平台
# 如果需要，可以根据需要更改此设置
ENV DOCKER_DEFAULT_PLATFORM=linux/amd64,linux/arm64

EXPOSE 8088

CMD java --enable-preview -jar -Xms50m -Xmx512m /usr/local/clouddisk-$VERSION-exec.jar --logging.level.root=warn --spring.profiles.active=prod --spring.data.mongodb.uri=$MONGODB_URI --file.rootDir=/jmalcloud/files --file.ip2region-db-path=/jmalcloud/ip2region.xdb
