FROM ubuntu:22.04 AS builder

# 设置非交互式，并安装所有构建/解压所需的工具
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    curl \
    unzip \
    p7zip-full \
    dpkg-dev && \
    rm -rf /var/lib/apt/lists/*

# 设置工作目录，避免污染根目录
WORKDIR /build

# 确定架构，以便下载正确的文件
ARG TARGETARCH
ENV ARCH=${TARGETARCH}

# mxcad https://www.mxdraw.com/download.html
# --- 下载所有必需的二进制文件和模型 ---
RUN wget https://repo.jellyfin.org/files/ffmpeg/ubuntu/latest-5.x/${ARCH}/jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb && \
    wget https://github.com/jamebal/jmal-cloud-server/releases/download/v2.14.0/mxcad_${ARCH}.zip && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/ubuntu-22.04-${ARCH}-bin.7z && \
    wget https://github.com/jamebal/OcrLiteOnnx/releases/download/v.1.8.2/models.7z

# --- 解压和准备文件 ---
# 创建目标目录
RUN mkdir -p \
    /output/jellyfin-ffmpeg \
    /output/mxcad \
    /output/ocrlite/bin \
    /output/ocrlite/models

# 使用 dpkg-deb 解压 .deb 包，而不是安装它
RUN dpkg-deb -x jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb /output/jellyfin-ffmpeg

# 解压其他应用
RUN unzip -o mxcad_${ARCH}.zip -d /output/mxcad_tmp && \
    mv /output/mxcad_tmp/mxcad_${ARCH} /output/mxcad && \
    rm -rf /output/mxcad_tmp

RUN 7z x ubuntu-22.04-${ARCH}-bin.7z -o/output/ocrlite_tmp && \
    mv /output/ocrlite_tmp/ubuntu-22.04-${ARCH}-bin/Linux-BIN/OcrLiteOnnx /output/ocrlite/bin/ && \
    rm -rf /output/ocrlite_tmp

RUN 7z x models.7z -o/output/ocrlite/models

# --- 设置权限 ---
RUN chmod -R 777 /output/mxcad/mxcadassembly && \
    chmod -R 777 /output/mxcad/mx/so/* && \
    chmod +x /output/ocrlite/bin/OcrLiteOnnx

FROM ubuntu:22.04

# --- 安装运行时依赖 ---
# 只安装服务运行时真正需要的包
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    locales \
    tesseract-ocr \
    unrar \
    libheif-examples \
    # 添加 jellyfin-ffmpeg 和 mxcad 可能需要的共享库
    libass9 libva-drm2 libva-x11-2 libvdpau1 libvorbisenc2 libwebp7 libx264-164 libx265-199 libxvidcore4 libfontconfig1 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# --- 配置系统环境 (Locale, Timezone) ---
RUN locale-gen en_US.UTF-8 && \
    update-locale LANG=en_US.UTF-8 && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# --- 复制 Java 运行时 ---
# 直接从官方 JRE 镜像中复制，高效且干净
COPY --from=eclipse-temurin:17-jre /opt/java/openjdk /opt/java/openjdk

# --- 从构建器阶段复制处理好的应用文件 ---
# 这是多阶段构建的核心！只复制纯净的、最终需要的文件。
COPY --from=builder /output/jellyfin-ffmpeg/usr/lib/jellyfin-ffmpeg /usr/lib/jellyfin-ffmpeg
COPY --from=builder /output/jellyfin-ffmpeg/usr/bin/ffmpeg /usr/bin/ffmpeg
COPY --from=builder /output/jellyfin-ffmpeg/usr/bin/ffprobe /usr/bin/ffprobe

COPY --from=builder /output/mxcad /usr/local/mxcad
COPY --from=builder /output/mxcad/mx/locale /usr/local/share/locale

COPY --from=builder /output/ocrlite/bin/OcrLiteOnnx /usr/local/bin/OcrLiteOnnx
COPY --from=builder /output/ocrlite/models /jmalcloud/models

# --- 复制配置文件和数据 ---
RUN mkdir -p /jmalcloud/files /jmalcloud/tess4j/datapath
COPY docker/ip2region.xdb /jmalcloud/
COPY tess4j/datapath/chi_sim.traineddata /jmalcloud/tess4j/datapath/
COPY target/lib /usr/local/clouddisk-lib

# --- 设置环境变量 ---
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    # 将 Java 和 ffmpeg 添加到 PATH
    PATH="/opt/java/openjdk/bin:/usr/lib/jellyfin-ffmpeg:${PATH}"

# 定义默认命令
CMD ["bash"]
