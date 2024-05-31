FROM eclipse-temurin:17-jre

# 安装 ffmpeg 和 tesseract
RUN apt-get update && \
    apt-get install -y ffmpeg libavcodec-extra locales tesseract-ocr && \
    locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8

# 设置环境变量
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

RUN mkdir -p /jmalcloud/files /jmalcloud/tess4j/datapath

ADD docker/ip2region.xdb /jmalcloud/

ADD tess4j/datapath/chi_sim.traineddata /jmalcloud/tess4j/datapath/

ADD target/lib /usr/local/clouddisk-lib
