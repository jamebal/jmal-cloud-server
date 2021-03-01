FROM jmal/mongo:latest
MAINTAINER zhushilun084@163.com
ENV VERSION 2.1.5
# 支持中文
ENV LANG en_US.UTF-8

# 时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' >/etc/timezone

# 安装java环境
ADD jdk-8u251-linux-x64.tar.gz /usr/local/
ENV MYPATH /usr/local
WORKDIR $MYPATH
ENV JAVA_HOME /usr/local/jdk1.8.0_251
ENV CLASSPATH $JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
ENV PATH $PATH:$JAVA_HOME/bin:$CLASSPATH

RUN rm -f /usr/local/jdk-8u251-linux-x64.tar.gz

RUN mkdir -p /jmalcloud/files /jmal-cloud-view/dist

COPY nginx.conf /etc/nginx/nginx.conf

COPY README.md /usr/local/README.md

# ip2region.db
ADD ip2region.db /jmalcloud/

# jmalcloud 后端
ADD clouddisk-$VERSION-exec.jar /usr/local/
# jmalcloud 前端
ADD dist-$VERSION.tar /jmal-cloud-view/

VOLUME /jmal-cloud-view/ /jmalcloud/

EXPOSE 8088 8080

CMD /usr/bin/mongod --fork --logpath /var/log/mongodb/mongodb.log && /etc/nginx/sbin/nginx && java -jar -Xms50m -Xmx512m clouddisk-$VERSION-exec.jar --logging.level.root=warn --spring.profiles.active=prod --file.rootDir=/jmalcloud/files --file.ip2region-db-path=/jmalcloud/ip2region.db
