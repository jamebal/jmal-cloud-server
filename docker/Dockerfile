FROM jmal/mongo:latest
MAINTAINER zhushilun084@163.com

# 支持中文
ENV LANG en_US.UTF-8

# 安装java环境
ADD jdk-8u251-linux-x64.tar.gz /usr/local/
ENV MYPATH /usr/local
WORKDIR $MYPATH
ENV JAVA_HOME /usr/local/jdk1.8.0_251
ENV CLASSPATH $JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
ENV PATH $PATH:$JAVA_HOME/bin:$CLASSPATH

RUN rm -f /usr/local/jdk-8u251-linux-x64.tar.gz

COPY README.md /usr/local/README.md

# jmalcloud 后端
ADD clouddisk-1.0-exec.jar /usr/local/
# jmalcloud 前端
ADD dist.tar /jmal-cloud-view/

VOLUME /jmal-cloud-view/dist /jmalcloud/files

EXPOSE 8088 80 443 27017 10010

CMD /etc/nginx/sbin/nginx && /usr/bin/mongod --fork --logpath /var/log/mongodb/mongodb.log && java -jar -Xms50m -Xmx512m clouddisk-1.0-exec.jar --file.rootDir=/jmalcloud/files --file.chunkFileDir=chunkFileDirTemp
