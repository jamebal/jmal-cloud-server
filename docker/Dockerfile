FROM openjdk:8u342-jre
MAINTAINER zhushilun084@163.com

ARG version=1.0.0

ENV VERSION $version

ENV MONGODB_URI "mongodb://mongo:27017/jmalcloud"

RUN mkdir -p /jmalcloud/files /jmal-cloud-view/dist

COPY nginx.conf /etc/nginx/nginx.conf

COPY README.md /usr/local/README.md

# ip2region.db
ADD ip2region.db /jmalcloud/

# jmalcloud 后端 需先把 clouddisk-$VERSION-exec.jar 复制到此目录
ADD clouddisk-$VERSION-exec.jar /usr/local/
# jmalcloud 前端 需先把 dist-$VERSION.tar 复制到此目录
ADD dist-$VERSION.tar /jmal-cloud-view/

VOLUME /jmal-cloud-view/ /jmalcloud/

EXPOSE 8088

CMD java -jar -Xms50m -Xmx512m /usr/local/clouddisk-$VERSION-exec.jar --logging.level.root=warn --spring.data.mongodb.uri=$MONGODB_URI --file.rootDir=/jmalcloud/files --file.ip2region-db-path=/jmalcloud/ip2region.db
