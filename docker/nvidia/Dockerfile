FROM nvidia/cuda:11.7.1-base-ubuntu22.04 AS base

# 设置环境变量，防止在安装过程中出现对话框干扰
ENV DEBIAN_FRONTEND=noninteractive

# 安装必要的软件包和依赖项
RUN apt-get update && \
    apt-get install -y \
    wget \
    gnupg \
    software-properties-common

# 添加 Jellyfin 的官方 GPG 密钥
RUN wget -O - https://repo.jellyfin.org/ubuntu/jellyfin_team.gpg.key | gpg --dearmor -o /usr/share/keyrings/jellyfin-archive-keyring.gpg

# 添加 Jellyfin 的官方 APT 仓库
RUN echo "deb [signed-by=/usr/share/keyrings/jellyfin-archive-keyring.gpg] https://repo.jellyfin.org/ubuntu focal main" > /etc/apt/sources.list.d/jellyfin.list

# 更新软件包列表并安装jellyfin-ffmpeg
RUN apt-get update && \
    apt-get install -y \
    jellyfin-ffmpeg

# 设置 ffmpeg 环境变量，使用 Jellyfin 的 ffmpeg
ENV PATH="/usr/lib/jellyfin-ffmpeg:$PATH"

# 清理安装后的无用文件，减小镜像体积
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

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
