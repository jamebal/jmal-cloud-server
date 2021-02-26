# Base images 基础镜像
FROM centos:centos7

#MAINTAINER 维护者信息
MAINTAINER jmal zhushilun084@163.com

#安装相关依赖
RUN yum -y update
RUN yum -y install  gcc gcc-c++ autoconf automake make
RUN yum -y install  zlib zlib-devel openssl* pcre* wget lua-devel

#ADD  获取url中的文件,放在当前目录下
ADD http://nginx.org/download/nginx-1.18.0.tar.gz /tmp/
#LuaJIT 2.1
ADD https://github.com/LuaJIT/LuaJIT/archive/v2.0.5.tar.gz /tmp/
# mod_zip 模块
COPY mod_zip.tar /tmp/

#切换目录
WORKDIR  /tmp

#安装LuaJIT 2.0.5
#RUN wget http://luajit.org/download/LuaJIT-2.0.5.tar.gz -P /tmp/
RUN tar zxf v2.0.5.tar.gz
WORKDIR  /tmp/LuaJIT-2.0.5
#RUN cd LuaJIT-2.0.5
RUN make PREFIX=/usr/local/luajit
RUN make install PREFIX=/usr/local/luajit

#安装 mod_zip 模块
WORKDIR  /tmp
RUN tar -xzf mod_zip.tar
RUN cp -r mod_zip/ /usr/local/src/

#设置环境变量
RUN export LUAJIT_LIB=/usr/local/lib
RUN export LUAJIT_INC=/usr/local/include/luajit-2.0

RUN mkdir -p {/usr/local/nginx/logs,/var/lock}

#编译安装Nginx
RUN useradd -M -s /sbin/nologin nginx
RUN tar -zxvf nginx-1.18.0.tar.gz
RUN mkdir -p /usr/local/nginx
RUN cd /tmp/nginx-1.18.0 \
    && ./configure --prefix=/etc/nginx --user=nginx --group=nginx \
    --conf-path=/etc/nginx/nginx.conf \
    --error-log-path=/var/log/nginx/error.log \
    --http-log-path=/var/log/nginx/access.log \
    --pid-path=/var/run/nginx.pid \
    --lock-path=/var/run/nginx.lock \
    --with-ld-opt="-Wl,-rpath,/usr/local/luajit/lib" \
    --with-http_stub_status_module \
    --with-http_ssl_module \
    --with-http_sub_module \
    --add-module=/usr/local/src/mod_zip \
    && make && make install
#参数说明
#--prefix 用于指定nginx编译后的安装目录
#--add-module 为添加的第三方模块，此次添加了fdfs的nginx模块
#--with..._module 表示启用的nginx模块，如此处启用了http_ssl_module模块

WORKDIR /etc/nginx

RUN rm -rf /tmp

RUN mkdir -p /jmalcloud/files /jmal-cloud-view/dist

RUN /etc/nginx/sbin/nginx -c /etc/nginx/nginx.conf
RUN ln -s /usr/local/nginx/sbin/* /usr/local/sbin/

VOLUME /etc/nginx/ /var/log/nginx/

#EXPOSE 映射端口
EXPOSE 80

#CMD 运行以下命令
CMD ["/etc/nginx/sbin/nginx","-g","daemon off;"]
