FROM eclipse-temurin:17-jre

# 安装 ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg libavcodec-extra && apt-get install -y locales && \
                                                      locale-gen en_US.UTF-8 && \
                                                      update-locale LANG=en_US.UTF-8


# 安装 Tomcat
RUN wget https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.89/bin/apache-tomcat-9.0.89.tar.gz && \
    tar xzf apache-tomcat-9.0.89.tar.gz && \
    mv apache-tomcat-9.0.89 /usr/local/tomcat && \
    rm apache-tomcat-9.0.89.tar.gz

# 删除默认的 webapps 目录下的内容
RUN rm -rf /usr/local/tomcat/webapps/*

# 下载war包
RUN wget https://github.com/jgraph/drawio/releases/download/v24.4.10/draw.war -O /usr/local/tomcat/webapps/draw.war

# 设置环境变量
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

RUN mkdir -p /jmalcloud/files

ADD docker/ip2region.xdb /jmalcloud/

ADD target/lib /usr/local/clouddisk-lib
