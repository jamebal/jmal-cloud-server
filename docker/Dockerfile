FROM nvidia/cuda:11.7.1-base-ubuntu22.04 AS base

# 设置非交互式安装，避免 tzdata 等包的配置暂停
ENV DEBIAN_FRONTEND=noninteractive

# 安装 wget 和 tesseract，并配置时区和 locales
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget unzip curl locales tesseract-ocr p7zip-full unrar libheif-examples && \
    locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8 && \
    # 下载并安装 jellyfin-ffmpeg
    ARCH=$(dpkg --print-architecture) && \
    wget https://repo.jellyfin.org/files/ffmpeg/ubuntu/latest-5.x/${ARCH}/jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb && \
    dpkg -i jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb || apt-get install -fy && \
    # 安装 mxcad https://www.mxdraw.com/download.html
    wget https://github.com/jamebal/jmal-cloud-server/releases/download/v2.14.0/mxcad_${ARCH}.zip && \
    unzip -o mxcad_${ARCH}.zip -d /usr/local/ && \
    mv /usr/local/mxcad_${ARCH}/ /usr/local/mxcad && \
    chmod -R 777 /usr/local/mxcad/mxcadassembly && \
    chmod -R 777 /usr/local/mxcad/mx/so/* && \
    cp -r -f /usr/local/mxcad/mx/locale /usr/local/share/locale && \
    # 安装 OcrLiteOnnx
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/ubuntu-22.04-${ARCH}-bin.7z && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/models.7z && \
    7z x models.7z -o/jmalcloud/ && \
    7z x ubuntu-22.04-${ARCH}-bin.7z -o/usr/local/ && \
    mv /usr/local/ubuntu-22.04-${ARCH}-bin/Linux-BIN/OcrLiteOnnx /usr/local/bin/OcrLiteOnnx && \
    # 卸载 wget 并清理下载的文件和APT缓存
    apt-get remove -y wget unzip && \
    apt-get clean && \
    rm -f jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb && \
    rm -rf /var/lib/apt/lists/* && \
    rm -f mxcad_${ARCH}.zip && \
    rm -rf /usr/local/ubuntu-22.04-${ARCH}-bin && \
    rm -f ubuntu-22.04-${ARCH}-bin.7z && \
    rm -f models.7z && \
    rm -rf /usr/local/__MACOSX

# 将/usr/lib/jellyfin-ffmpeg添加到PATH
ENV PATH=/usr/lib/jellyfin-ffmpeg:$PATH

# 环境变量
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    LD_LIBRARY_PATH=/usr/local/nvidia/lib:/usr/local/nvidia/lib64 \
    NVIDIA_DRIVER_CAPABILITIES=compute,utility,video \
    NVIDIA_VISIBLE_DEVICES=all

FROM eclipse-temurin:17-jre AS java-base

# 合并 CUDA 和 Java 环境
FROM base AS final
COPY --from=java-base /opt/java/openjdk /opt/java/openjdk

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

RUN mkdir -p /jmalcloud/files /jmalcloud/tess4j/datapath


COPY docker/ip2region.xdb /jmalcloud/

COPY tess4j/datapath/chi_sim.traineddata /jmalcloud/tess4j/datapath/
COPY target/lib /usr/local/clouddisk-lib

# 更新 PATH 和 LD_LIBRARY_PATH
ENV PATH="/opt/java/openjdk/bin:${PATH}"

CMD ["bash"]
