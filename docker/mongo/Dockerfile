FROM jmal/nginx:latest

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

COPY docker-entrypoint.sh /usr/local/bin/

#EXPOSE 映射端口
EXPOSE 80 443 27017

#CMD 运行以下命令
CMD /etc/nginx/sbin/nginx && /usr/bin/mongod
