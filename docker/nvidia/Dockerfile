FROM nvidia/cuda:11.7.1-base-ubuntu22.04 AS base

# 设置非交互式安装，避免 tzdata 等包的配置暂停
ENV DEBIAN_FRONTEND=noninteractive

# 安装wget
RUN apt-get update && apt-get install -y wget && apt-get install -y locales && \
                                                  locale-gen en_US.UTF-8 && \
                                                  update-locale LANG=en_US.UTF-8

ARG ARCH=amd64

# 下载jellyfin-ffmpeg.deb
RUN wget https://repo.jellyfin.org/files/ffmpeg/ubuntu/latest-5.x/${ARCH}/jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb

# 安装下载的 jellyfin-ffmpeg.deb
RUN dpkg -i jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb || apt-get install -fy

# 卸载 wget 并清理
RUN apt-get remove -y wget && apt-get clean && rm -rf /var/lib/apt/lists/*

# 清除安装文件
RUN rm -rf jellyfin-ffmpeg5_5.1.4-3-jammy_${ARCH}.deb

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

RUN mkdir -p /jmalcloud/files

ADD docker/ip2region.xdb /jmalcloud/

ADD target/lib /usr/local/clouddisk-lib

# 更新 PATH 和 LD_LIBRARY_PATH
ENV PATH="/opt/java/openjdk/bin:${PATH}"

CMD ["bash"]
