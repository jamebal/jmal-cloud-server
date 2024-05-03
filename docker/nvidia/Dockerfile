FROM nvidia/cuda:11.7.1-base-ubuntu22.04 AS base

RUN apt-get update && \
    apt-get install -y locales ffmpeg libavcodec-extra && \
    locale-gen en_US.UTF-8

# 环境变量
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    LD_LIBRARY_PATH=/usr/local/nvidia/lib:/usr/local/nvidia/lib64 \
    NVIDIA_DRIVER_CAPABILITIES=compute,utility,video \
    NVIDIA_VISIBLE_DEVICES=all

# 设置环境变量
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

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
