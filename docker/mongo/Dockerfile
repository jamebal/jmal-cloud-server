FROM registry.cn-guangzhou.aliyuncs.com/jmalcloud/jmal-nginx:latest

## 安装mongodb
RUN echo -e "\
[mongodb]\n\
name=MongoDB Repository\n\
baseurl=https://repo.mongodb.org/yum/redhat/7Server/mongodb-org/4.0/x86_64/\n\
gpgcheck=0\n\
enabled=1\n" >> /etc/yum.repos.d/mongodb.repo

RUN yum update -y && yum install -y mongodb-org

RUN mkdir -p /data/db /data/configdb /var/log/mongodb /var/run/mongodb

VOLUME /data/db /data/configdb

RUN mkdir -p /tmp

# 支持中文
ENV LANG en_US.UTF-8

# 时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' >/etc/timezone

# 安装java环境
ADD jdk-8u281-linux-x64.tar.gz /usr/local/
ENV MYPATH /usr/local
WORKDIR $MYPATH
ENV JAVA_HOME /usr/local/jdk1.8.0_281
ENV CLASSPATH $JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
ENV PATH $PATH:$JAVA_HOME/bin:$CLASSPATH

RUN rm -f /usr/local/jdk-8u281-linux-x64.tar.gz

COPY docker-entrypoint.sh /usr/local/bin/

#EXPOSE 映射端口
EXPOSE 27017

#CMD 运行以下命令
CMD /etc/nginx/sbin/nginx && /usr/bin/mongod --bind_ip=0.0.0.0
