FROM eclipse-temurin:17-jre

# 安装 FFmpeg
RUN apt-get update && \
    apt-get install -y software-properties-common && \
    add-apt-repository ppa:jonathonf/ffmpeg-5 && \
    apt-get update && \
    apt-get install -y ffmpeg libavcodec-extra

# 环境变量
ENV LD_LIBRARY_PATH=/usr/local/nvidia/lib:/usr/local/nvidia/lib64
ENV NVIDIA_DRIVER_CAPABILITIES=compute,utility
ENV NVIDIA_VISIBLE_DEVICES=all
