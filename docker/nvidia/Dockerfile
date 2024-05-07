FROM nvidia/cuda:11.7.1-base-ubuntu22.04 AS base

# 设置非交互式安装，避免 tzdata 等包的配置暂停
ENV DEBIAN_FRONTEND=noninteractive

# 安装依赖
RUN apt-get update && apt-get install -y \
    build-essential \
    yasm \
    cmake \
    git \
    pkg-config \
    libtool \
    g++ \
    make \
    wget \
    curl \
    libssl-dev \
    libx264-dev \
    libx265-dev \
    libnuma-dev \
    libvpx-dev \
    libfdk-aac-dev \
    libmp3lame-dev \
    libopus-dev \
    libsdl2-dev

# 获取 FFmpeg 源代码
RUN mkdir -p ~/ffmpeg_sources && cd ~/ffmpeg_sources && \
    git clone --depth 1 https://git.ffmpeg.org/ffmpeg.git ffmpeg && \
    cd ffmpeg

# 编译 FFmpeg
RUN cd ~/ffmpeg_sources/ffmpeg && \
    ./configure \
    --enable-gpl \
    --enable-libx264 \
    --enable-libx265 \
    --enable-libvpx \
    --enable-libfdk-aac \
    --enable-libmp3lame \
    --enable-libopus \
    --enable-nonfree \
    --enable-cuda-nvcc \
    --enable-libnpp \
    --enable-nvenc \
    --enable-cuvid \
    --enable-hwaccel=h264_nvenc \
    --enable-hwaccel=hevc_nvenc \
    --enable-hwaccel=h264_cuvid \
    --enable-hwaccel=hevc_cuvid \
    --extra-cflags=-I/usr/local/cuda/include \
    --extra-ldflags=-L/usr/local/cuda/lib64 && \
    make -j$(nproc) && \
    make install

# 清理
RUN apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf ~/ffmpeg_sources

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

RUN mkdir -p /jmalcloud/files

ADD docker/ip2region.xdb /jmalcloud/

ADD target/lib /usr/local/clouddisk-lib

# 更新 PATH 和 LD_LIBRARY_PATH
ENV PATH="/opt/java/openjdk/bin:${PATH}"

CMD ["bash"]
