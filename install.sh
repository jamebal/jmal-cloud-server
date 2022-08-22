#!/bin/bash

#fonts color
Green="\033[32m"
Red="\033[31m"
GreenBG="\033[42;37m"
RedBG="\033[41;37m"
Font="\033[0m"

#notification information
OK="${Green}[OK]${Font}"
Error="${Red}[错误]${Font}"

cur_path="$(pwd)"
cur_arg=$@
COMPOSE="docker-compose"

#notification information
Error="${Red}[Error]${Font}"

cur_path="$(pwd)"
COMPOSE="docker-compose"

check_docker() {
    docker --version &> /dev/null
    if [ $? -ne  0 ]; then
        echo -e "${Error} ${RedBG} Not installed Docker！${Font}"
        exit 1
    fi
    docker-compose version &> /dev/null
    if [ $? -ne  0 ]; then
        docker compose version &> /dev/null
        if [ $? -ne  0 ]; then
            echo -e "${Error} ${RedBG} Not installed Docker-compose！${Font}"
            exit 1
        fi
        COMPOSE="docker compose"
    fi
    if [[ -n `$COMPOSE version | grep -E "\sv*1"` ]]; then
        $COMPOSE version
        echo -e "${Error} ${RedBG} Docker-compose The version is too early. Please upgrade to v2+！${Font}"
        exit 1
    fi
}

rand() {
    local min=$1
    local max=$(($2-$min+1))
    local num=$(($RANDOM+1000000000))
    echo $(($num%$max+$min))
}

env_get() {
    local key=$1
    local value=`cat ${cur_path}/.env | grep "^$key=" | awk -F '=' '{print $2}'`
    echo "$value"
}

env_set() {
    local key=$1
    local val=$2
    local exist=`cat ${cur_path}/.env | grep "^$key="`
    if [ -z "$exist" ]; then
        echo "$key=$val" >> $cur_path/.env
    else
        if [[ `uname` == 'Linux' ]]; then
            sed -i "/^${key}=/c\\${key}=${val}" ${cur_path}/.env
        else
            docker run -it --rm -v ${cur_path}:/www alpine sh -c "sed -i "/^${key}=/c\\${key}=${val}" /www/.env"
        fi
        if [ $? -ne  0 ]; then
            echo -e "${Error} ${RedBG} Failed to set env parameter! ${Font}"
            exit 1
        fi
    fi
}

is_arm() {
    local get_arch=`arch`
    if [[ $get_arch =~ "aarch" ]] || [[ $get_arch =~ "arm" ]]; then
        echo "yes"
    else
        echo "no"
    fi
}

arg_get() {
    local find="n"
    local value=""
    for var in $cur_arg; do
        if [[ "$find" == "y" ]]; then
            if [[ ! $var =~ "--" ]]; then
                value=$var
            fi
            break
        fi
        if [[ "--$1" == "$var" ]] || [[ "-$1" == "$var" ]]; then
            find="y"
            value="yes"
        fi
    done
    echo $value
}

run_mongo() {
    if [ "$1" = "backup" ]; then
        # 备份数据库
        database=$(env_get DB_DATABASE)
        username=$(env_get DB_USERNAME)
        password=$(env_get DB_PASSWORD)
        mkdir -p ${cur_path}/docker/mysql/backup
        filename="${cur_path}/docker/mysql/backup/${database}_$(date "+%Y%m%d%H%M%S").sql.gz"
        run_exec mariadb "exec mysqldump --databases $database -u$username -p$password" | gzip > $filename
        judge "备份数据库"
        [ -f "$filename" ] && echo -e "备份文件：$filename"
    elif [ "$1" = "recovery" ]; then
        # 还原数据库
        database=$(env_get DB_DATABASE)
        username=$(env_get DB_USERNAME)
        password=$(env_get DB_PASSWORD)
        mkdir -p ${cur_path}/docker/mysql/backup
        list=`ls -1 "${cur_path}/docker/mysql/backup" | grep ".sql.gz"`
        if [ -z "$list" ]; then
            echo -e "${Error} ${RedBG} 没有备份文件！${Font}"
            exit 1
        fi
        echo "$list"
        read -rp "请输入备份文件名称还原：" inputname
        filename="${cur_path}/docker/mysql/backup/${inputname}"
        if [ ! -f "$filename" ]; then
            echo -e "${Error} ${RedBG} 备份文件：${inputname} 不存在！ ${Font}"
            exit 1
        fi
        container_name=`docker_name mariadb`
        if [ -z "$container_name" ]; then
            echo -e "${Error} ${RedBG} 没有找到 mariadb 容器! ${Font}"
            exit 1
        fi
        docker cp $filename $container_name:/
        run_exec mariadb "gunzip < /$inputname | mysql -u$username -p$password $database"
        run_exec php "php artisan migrate"
        judge "还原数据库"
    fi
}

help() {
          echo "
  Usage:  install.sh COMMAND [OPTIONS]

  Options:
    --port               网盘端口,默认7070
    --blog_port          博客端口,默认7071
    --server_port        服务端口,默认7072(API地址: http://127.0.0.1:{server_port}/public/api)
    --prefix             容器名称前缀,默认为jmalcloud

  Management Commands:
    init,install         安装
"
}

env_init() {
  if [[ "$(is_arm)" == "yes" ]]; then
    env_set OFFICE_IMAGE "registry.cn-guangzhou.aliyuncs.com/jmalcloud/onlyoffice_documentserver:latest-arm64"
  else
    env_set OFFICE_IMAGE "registry.cn-guangzhou.aliyuncs.com/jmalcloud/onlyoffice_documentserver:7.0.0.132"
  fi
  env_set APP_IPPR "10.$(rand 50 100).$(rand 100 200)"
  env_set APP_PORT 7070
  env_set BLOG_PORT 7071
  env_set SERVER_PORT 7072
  env_set CONTAINER_NAME_PREFIX "jmalcloud"
}

########################################################################################################
check_docker
env_init

if [ $# -gt 0 ]; then
    if [[ "$1" == "init" ]] || [[ "$1" == "install" ]]; then
        shift 1
        # 初始化文件
        mkdir -p "${cur_path}/docker/www/"
        tar -xzf "${cur_path}/www/releases/dist-latest.tar" -C "${cur_path}/docker/www/"
        # 启动容器
        [[ "$(arg_get port)" -gt 0 ]] && env_set APP_PORT "$(arg_get port)"
        [[ "$(arg_get blog_port)" -gt 0 ]] && env_set BLOG_PORT "$(arg_get blog_port)"
        [[ "$(arg_get server_port)" -gt 0 ]] && env_set SERVER_PORT "$(arg_get server_port)"
        [[ "$(arg_get prefix)" -gt 0 ]] && env_set CONTAINER_NAME_PREFIX "$(arg_get prefix)"
        $COMPOSE up -d
        echo "${OK} ${GreenBG} 安装完成 ${Font}"
        echo "网盘地址: http://${GreenBG}127.0.0.1:$(env_get APP_PORT)${Font}"
        echo "博客地址: http://${GreenBG}127.0.0.1:$(env_get BLOG_PORT)${Font}"
        echo "API地址: http://${GreenBG}127.0.0.1:$(env_get SERVER_PORT)/public/api${Font}"
    elif [[ "$1" == "update" ]]; then
        shift 1
        run_mysql backup
        git fetch --all
        git reset --hard origin/$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')
        git pull
        run_exec php "composer update"
        run_exec php "php artisan migrate"
        supervisorctl_restart php
        $COMPOSE up -d
    elif [[ "$1" == "uninstall" ]]; then
        shift 1
        read -rp "确定要卸载（含：删除容器、数据库、日志）吗？(y/n): " uninstall
        [[ -z ${uninstall} ]] && uninstall="N"
        case $uninstall in
        [yY][eE][sS] | [yY])
            echo -e "${RedBG} 开始卸载... ${Font}"
            ;;
        *)
            echo -e "${GreenBG} 终止卸载。 ${Font}"
            exit 2
            ;;
        esac
        $COMPOSE down
        rm -rf "./docker/mysql/data"
        rm -rf "./docker/log/supervisor"
        find "./storage/logs" -name "*.log" | xargs rm -rf
        echo -e "${OK} ${GreenBG} 卸载完成 ${Font}"
    elif [[ "$1" == "reinstall" ]]; then
        shift 1
        ./cmd uninstall $@
        sleep 3
        ./cmd install $@
    elif [[ "$1" == "port" ]]; then
        shift 1
        env_set APP_PORT "$1"
        $COMPOSE up -d
        echo -e "${OK} ${GreenBG} 修改成功 ${Font}"
        echo -e "地址: http://${GreenBG}127.0.0.1:$(env_get APP_PORT)${Font}"
    elif [[ "$1" == "nginx" ]]; then
        shift 1
        e="nginx $@" && run_exec nginx "$e"
    elif [[ "$1" == "mysql" ]]; then
        shift 1
        if [ "$1" = "backup" ]; then
            run_mysql backup
        elif [ "$1" = "recovery" ]; then
            run_mysql recovery
        else
            e="mysql $@" && run_exec mariadb "$e"
        fi
    elif [[ "$1" == "restart" ]]; then
        shift 1
        $COMPOSE stop "$@"
        $COMPOSE start "$@"
    else
        echo "$1 is not a jmalcloud command."
    fi
else
    help
fi

## update
#
#git pull origin
#
## check_docker
#check_docker
#
## env_init
#env_init
#
## Start the container
#$COMPOSE up -d
#echo -e "${OK} ${GreenBG} 安装完成 ${Font}"
#echo -e "地址: http://${GreenBG}127.0.0.1:$(env_get APP_PORT)${Font}"
