FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive

WORKDIR /build
ARG TARGETARCH
ENV ARCH=${TARGETARCH}

RUN apt-get update && \
    apt-get install -y \
      build-essential \
      wget \
      git \
      pkg-config \
      libjpeg-dev \
      libpng-dev \
      libtiff-dev \
      libwebp-dev \
      libheif-dev \
      libde265-dev \
      libfftw3-dev \
      libopenexr-dev \
      libxml2-dev \
      libfreetype6-dev \
      liblcms2-dev \
      libfontconfig1-dev \
      libx11-dev \
      ca-certificates \
      --no-install-recommends \
      curl unzip p7zip-full && \
    rm -rf /var/lib/apt/lists/* && \
    \
    # 下载并编译安装 ImageMagick 7.x
    git clone --depth 1 --branch 7.1.2-1 https://github.com/ImageMagick/ImageMagick.git ImageMagick-7.1.2-1 && \
    cd ImageMagick-7.1.2-1 && \
    ./configure --prefix=/opt/imagemagick --disable-static && \
    make -j$(nproc) && \
    make install && \
    \
    # 下载相关包 \
    cd /build && \
    wget https://repo.jellyfin.org/files/ffmpeg/ubuntu/7.x/7.1.1-7/${ARCH}/jellyfin-ffmpeg7_7.1.1-7-jammy_${ARCH}.deb && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/ubuntu-22.04-${ARCH}-bin.7z && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/models.7z && \
    # 提取 ffmpeg 依赖 \
    \
    dpkg-deb -f jellyfin-ffmpeg7_7.1.1-7-jammy_${ARCH}.deb Depends | sed 's/,/ /g' | sed 's/|/ /g' | sed -E 's/\([^)]+\)//g' > ffmpeg_dependencies.txt && \
    mv jellyfin-ffmpeg7_7.1.1-7-jammy_${ARCH}.deb jellyfin-ffmpeg.deb && \
    \
    # --- 解压 OcrLiteOnnx ---
    mkdir -p /output/ocrlite/bin /output/ocrlite/models && \
    7z x ubuntu-22.04-${ARCH}-bin.7z -o/output/ocrlite_tmp && \
    mv /output/ocrlite_tmp/ubuntu-22.04-${ARCH}-bin/Linux-BIN/OcrLiteOnnx /output/ocrlite/bin/ && \
    rm -rf /output/ocrlite_tmp && \
    7z x models.7z -o/output/ocrlite/models && \
    \
    # --- 设置权限 ---
    mod +x /output/ocrlite/bin/OcrLiteOnnx


FROM ubuntu:22.04 as runtime

ENV DEBIAN_FRONTEND=noninteractive
# --- 安装 ffmpeg ---
COPY --from=builder /build/ffmpeg_dependencies.txt /build/jellyfin-ffmpeg.deb /tmp/

# 安装基础运行时依赖
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gosu \
    locales \
    tesseract-ocr \
    poppler-utils \
    p7zip-full \
    unrar \
    ca-certificates \
    libheif1 \
    libwebpdemux2 \
    libopenexr25 \
    $(cat /tmp/ffmpeg_dependencies.txt) && \
    dpkg -i /tmp/jellyfin-ffmpeg.deb && \
    # 清理工作
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/ffmpeg_dependencies.txt /tmp/jellyfin-ffmpeg.deb && \
    # --- 配置系统环境 (Locale, Timezone) ---
    locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8 && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    mkdir -p /jmalcloud/files /jmalcloud/tess4j/datapath

COPY --from=builder /output/ocrlite/bin/OcrLiteOnnx /usr/local/bin/OcrLiteOnnx
COPY --from=builder /output/ocrlite/models /jmalcloud/
COPY --from=builder /opt/imagemagick /opt/imagemagick

# --- 复制配置文件和数据 ---
COPY docker/ip2region.xdb tess4j /jmalcloud/

# --- 设置环境变量 ---
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    PATH="/opt/imagemagick/bin:/usr/lib/jellyfin-ffmpeg:${PATH}"

CMD ["bash"]
