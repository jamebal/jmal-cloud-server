FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    ca-certificates wget curl unzip p7zip-full && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build
ARG TARGETARCH
ENV ARCH=${TARGETARCH}

RUN wget https://repo.jellyfin.org/files/ffmpeg/ubuntu/7.x/7.1.1-7/${ARCH}/jellyfin-ffmpeg7_7.1.1-7-jammy_${ARCH}.deb && \
    wget https://github.com/jamebal/jmal-cloud-server/releases/download/v2.14.0/mxcad_${ARCH}.zip && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/ubuntu-22.04-${ARCH}-bin.7z && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/models.7z

RUN dpkg-deb -f jellyfin-ffmpeg7_7.1.1-7-jammy_${ARCH}.deb Depends | sed 's/,/ /g' | sed 's/|/ /g' | sed -E 's/\([^)]+\)//g' > ffmpeg_dependencies.txt

# --- 解压 mxcad 和 OcrLiteOnnx ---
RUN mkdir -p /output/ocrlite/bin /output/ocrlite/models && \
    unzip -o mxcad_${ARCH}.zip -d /output && \
    mv /output/mxcad_${ARCH} /output/mxcad && \
    7z x ubuntu-22.04-${ARCH}-bin.7z -o/output/ocrlite_tmp && \
    mv /output/ocrlite_tmp/ubuntu-22.04-${ARCH}-bin/Linux-BIN/OcrLiteOnnx /output/ocrlite/bin/ && \
    rm -rf /output/ocrlite_tmp && \
    7z x models.7z -o/output/ocrlite/models

# --- 设置权限 ---
RUN chmod -R 777 /output/mxcad/mxcadassembly && chmod -R 777 /output/mxcad/mx/so/* && chmod +x /output/ocrlite/bin/OcrLiteOnnx


FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
# --- 1. 安装基础运行时依赖 ---
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    locales \
    tesseract-ocr \
    imagemagick \
    poppler-utils \
    p7zip-full \
    unrar \
    libheif-examples && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# --- 2. 安装 ffmpeg ---
COPY --from=builder /build/ffmpeg_dependencies.txt /tmp/
COPY --from=builder /build/jellyfin-ffmpeg7_7.1.1-7-jammy_*.deb /tmp/jellyfin-ffmpeg.deb

RUN apt-get update && \
    apt-get install -y --no-install-recommends $(cat /tmp/ffmpeg_dependencies.txt) && \
    dpkg -i /tmp/jellyfin-ffmpeg.deb && \
    # 清理工作
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/ffmpeg_dependencies.txt /tmp/jellyfin-ffmpeg.deb

# --- 3. 配置系统环境 (Locale, Timezone) ---
RUN locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8 && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# --- 4. 从构建器阶段复制处理好的应用文件 ---
COPY --from=builder /output/mxcad /usr/local/mxcad
COPY --from=builder /output/mxcad/mx/locale /usr/local/share/locale
COPY --from=builder /output/ocrlite/bin/OcrLiteOnnx /usr/local/bin/OcrLiteOnnx
COPY --from=builder /output/ocrlite/models /jmalcloud/

# --- 5. 复制配置文件和数据 ---
RUN mkdir -p /jmalcloud/files /jmalcloud/tess4j/datapath
COPY docker/ip2region.xdb /jmalcloud/
COPY tess4j/datapath/chi_sim.traineddata /jmalcloud/tess4j/datapath/

# --- 6. 设置环境变量 ---
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    PATH="/usr/lib/jellyfin-ffmpeg:${PATH}"

CMD ["bash"]
