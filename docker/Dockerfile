FROM openjdk:17-jdk-alpine

MAINTAINER zhushilun084@gmail.com

ARG version=1.0.0

ENV VERSION $version

ENV MONGODB_URI "mongodb://mongo:27017/jmalcloud"

RUN mkdir -p /jmalcloud/files

ADD ip2region.xdb /jmalcloud/

ADD clouddisk-$VERSION-exec.jar /usr/local/

VOLUME /jmalcloud/

EXPOSE 8088

CMD java -jar -Xms50m -Xmx512m /usr/local/clouddisk-$VERSION-exec.jar --logging.level.root=warn --spring.profiles.active=prod --spring.data.mongodb.uri=$MONGODB_URI --file.rootDir=/jmalcloud/files --file.ip2region-db-path=/jmalcloud/ip2region.xdb
