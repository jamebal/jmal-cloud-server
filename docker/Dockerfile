FROM eclipse-temurin:17-jre

# 安装 ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg libavcodec-extra && apt-get install -y locales && \
                                                      locale-gen en_US.UTF-8 && \
                                                      update-locale LANG=en_US.UTF-8

# 设置环境变量
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

RUN mkdir -p /jmalcloud/files

ADD docker/ip2region.xdb /jmalcloud/

ADD target/lib /usr/local/clouddisk-lib
