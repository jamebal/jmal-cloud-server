FROM eclipse-temurin:17-jre

# 安装 ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg libavcodec-extra

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

RUN mkdir -p /jmalcloud/files

ADD docker/ip2region.xdb /jmalcloud/

ADD target/lib /usr/local/clouddisk-lib
