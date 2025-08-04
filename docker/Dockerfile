FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget curl unzip p7zip-full && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build
ARG TARGETARCH
ENV ARCH=${TARGETARCH}

# --- 下载所有资源 ---
# mxcad https://www.mxdraw.com/download.html
RUN wget https://repo.jellyfin.org/files/ffmpeg/ubuntu/latest-5.x/${ARCH}/jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb && \
    wget https://github.com/jamebal/jmal-cloud-server/releases/download/v2.14.0/mxcad_${ARCH}.zip && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/ubuntu-22.04-${ARCH}-bin.7z && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/models.7z

# --- 解压 mxcad 和 OcrLiteOnnx ---
# 这部分内容将被干净地复制到最终镜像
RUN mkdir -p /output/mxcad /output/ocrlite/bin /output/ocrlite/models
RUN unzip -o mxcad_${ARCH}.zip -d /output/mxcad_tmp && mv /output/mxcad_tmp/mxcad_${ARCH} /output/mxcad && rm -rf /output/mxcad_tmp
RUN 7z x ubuntu-22.04-${ARCH}-bin.7z -o/output/ocrlite_tmp && mv /output/ocrlite_tmp/ubuntu-22.04-${ARCH}-bin/Linux-BIN/OcrLiteOnnx /output/ocrlite/bin/ && rm -rf /output/ocrlite_tmp
RUN 7z x models.7z -o/output/ocrlite/models

# --- 设置权限 ---
RUN chmod -R 777 /output/mxcad/mxcadassembly && chmod -R 777 /output/mxcad/mx/so/* && chmod +x /output/ocrlite/bin/OcrLiteOnnx


# =================================================================
# 阶段 2: 最终镜像 (Final Image)
# =================================================================
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    locales \
    tesseract-ocr \
    p7zip-full \
    unrar \
    libheif-examples && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/jellyfin-ffmpeg5_5.1.4-3-jammy_*.deb /tmp/jellyfin-ffmpeg.deb
RUN dpkg -i /tmp/jellyfin-ffmpeg.deb || apt-get -f install -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/jellyfin-ffmpeg.deb

# --- 3. 配置系统环境 (Locale, Timezone) ---
RUN locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8 && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# --- 4. 复制 Java 运行时 ---
COPY --from=eclipse-temurin:17-jre /opt/java/openjdk /opt/java/openjdk

# --- 5. 从构建器阶段复制处理好的应用文件 ---
COPY --from=builder /output/mxcad /usr/local/mxcad
COPY --from=builder /output/mxcad/mx/locale /usr/local/share/locale
COPY --from=builder /output/ocrlite/bin/OcrLiteOnnx /usr/local/bin/OcrLiteOnnx
COPY --from=builder /output/ocrlite/models /jmalcloud/models

# --- 6. 复制配置文件和数据 ---
RUN mkdir -p /jmalcloud/files /jmalcloud/tess4j/datapath
COPY docker/ip2region.xdb /jmalcloud/
COPY tess4j/datapath/chi_sim.traineddata /jmalcloud/tess4j/datapath/
COPY target/lib /usr/local/clouddisk-lib

# --- 7. 设置环境变量 ---
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    PATH="/opt/java/openjdk/bin:/usr/lib/jellyfin-ffmpeg:${PATH}"

CMD ["bash"]
