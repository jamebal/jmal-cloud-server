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

check_docker() {
  docker --version &>/dev/null
  if [ $? -ne 0 ]; then
    echo "${Error} ${RedBG} 未安装 Docker！${Font}"
    exit 1
  fi
  docker-compose version &>/dev/null
  if [ $? -ne 0 ]; then
    docker compose version &>/dev/null
    if [ $? -ne 0 ]; then
      echo "${Error} ${RedBG} 未安装 Docker-compose！${Font}"
      exit 1
    fi
    COMPOSE="docker compose"
  fi
  if [[ -n $($COMPOSE version | grep -E "\sv*1") ]]; then
    $COMPOSE version
    echo "${Error} ${RedBG} Docker-compose 的版本太低了，请升级到 v2+！${Font}"
    exit 1
  fi
}

run_exec() {
  local container=$1
  local cmd=$2
  local name="$(env_get CONTAINER_NAME_PREFIX)_$container"
  if [ -z "$name" ]; then
    echo "${Error} ${RedBG} 没有找到 $container 容器! ${Font}"
    exit 1
  fi
  docker exec -it "$name" /bin/sh -c "$cmd"
}

judge() {
  if [[ 0 -eq $? ]]; then
    echo "${OK} ${GreenBG} $1 完成${Font}"
    sleep 1
  else
    echo "${Error} ${RedBG} $1 失败${Font}"
    exit 1
  fi
}

rand() {
  local min=$1
  local max=$(($2 - $min + 1))
  local num=$(($RANDOM + 1000000000))
  echo $(($num % $max + $min))
}

env_get() {
  local key=$1
  local value=$(cat ${cur_path}/.env | grep "^$key=" | awk -F '=' '{print $2}')
  echo "$value"
}

env_set() {
  local key=$1
  local val=$2
  local exist=$(cat ${cur_path}/.env | grep "^$key=")
  if [ -z "$exist" ]; then
    echo "$key=$val" >>$cur_path/.env
  else
    if [[ $(uname) == 'Linux' ]]; then
      sed -i "/^${key}=/c\\${key}=${val}" ${cur_path}/.env
    fi
    if [ $? -ne 0 ]; then
      echo "${Error} ${RedBG} 设置env参数失败! ${Font}"
      exit 1
    fi
  fi
}

is_arm() {
  local get_arch=$(arch)
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
  if [ "$1" = "dump" ]; then
    # 备份数据库
    mkdir -p ${cur_path}/docker/mongodb/backup
    run_exec mongodb "mongodump -d jmalcloud -o /dump --gzip --quiet"
    judge "备份数据库"
  elif [ "$1" = "restore" ]; then
    # 恢复数据库
    run_exec mongodb "mongorestore -d jmalcloud --dir /dump/jmalcloud --gzip --quiet"
    judge "恢复数据库"
  fi
}

help() {
  echo "
  Usage:  install.sh COMMAND [OPTIONS]

  Options:
    --port               网盘端口,默认7070
    --blog_port          博客端口,默认7071
    --server_port        服务端口,默认7072(API地址: http://127.0.0.1:7072/public/api)
    --prefix             容器名称前缀,默认为jmalcloud

  Management Commands:
    init,install         安装
    uninstall            卸载
    update               更新
    dump                 备份数据库
    restore              恢复数据库
    restart              重启
    reinstall            卸载后再安装
"
}

env_init() {
  if [[ "$(is_arm)" == "yes" ]]; then
    env_set OFFICE_IMAGE_VERSION "latest-arm64"
    env_set DOCKER_ARCH "-arm64"
  else
    env_set OFFICE_IMAGE_VERSION "7.0.0.132"
    env_set DOCKER_ARCH ""
  fi
  env_set APP_IPPR "10.$(rand 50 100).$(rand 100 200)"
  env_set APP_PORT 7070
  env_set BLOG_PORT 7071
  env_set SERVER_PORT 7072
  env_set CONTAINER_NAME_PREFIX "jmalcloud"
}

before_start() {
  [[ "$(arg_get port)" -gt 0 ]] && env_set APP_PORT "$(arg_get port)"
  [[ "$(arg_get blog_port)" -gt 0 ]] && env_set BLOG_PORT "$(arg_get blog_port)"
  [[ "$(arg_get server_port)" -gt 0 ]] && env_set SERVER_PORT "$(arg_get server_port)"
  [[ "$(arg_get prefix)" -gt 0 ]] && env_set CONTAINER_NAME_PREFIX "$(arg_get prefix)"
}

install() {
  # 初始化文件
  mkdir -p "${cur_path}/docker/www/"
  tar -xzf "${cur_path}/www/releases/dist-latest.tar" -C "${cur_path}/docker/www/"
  # 启动容器
  before_start
  $COMPOSE up -d
  # 检测服务是否启动
  check_run "安装"
}

uninstall() {
  read -rp "确定要卸载（含：删除容器、所有文件、数据库、日志）吗？(y/n): " uninstall
  [[ -z ${uninstall} ]] && uninstall="N"
  case $uninstall in
  [yY][eE][sS] | [yY])
    echo "${RedBG} 开始卸载... ${Font}"
    ;;
  *)
    echo "${GreenBG} 终止卸载。 ${Font}"
    exit 2
    ;;
  esac
  $COMPOSE down
  rm -rf "./docker/mongodb/data"
  rm -rf "./docker/mongodb/data"
  rm -rf "./docker/jmalcloud"
  echo "${OK} ${GreenBG} 卸载完成 ${Font}"
}

check_run() {
  server_url="http://127.0.0.1:$(env_get BLOG_PORT)"
  for ((i = 1; i <= 30; i++)); do
    url_status=$(curl -s -m 5 -IL $server_url | grep 200)
    if [ "$url_status" != "" ]; then
      echo "${OK} ${GreenBG} $1完成 ${Font}"
      echo "网盘地址: http://127.0.0.1:$(env_get APP_PORT)"
      echo "博客地址: $server_url"
      echo "API地址: http://127.0.0.1:$(env_get SERVER_PORT)/public/api"
      break
    else
      sleep 5
    fi
  done
  url_status=$(curl -s -m 5 -IL $server_url | grep 200)
  if [ "$url_status" == "" ]; then
    echo "${Error} ${RedBG} $1失败，反馈给开发者: https://github.com/jamebal/jmal-cloud-view/issues${Font}"
  fi
}

########################################################################################################
check_docker
env_init

if [ $# -gt 0 ]; then
  if [[ "$1" == "init" ]] || [[ "$1" == "install" ]]; then
    shift 1
    install
  elif [[ "$1" == "update" ]]; then
    shift 1
    run_mongo backup
    #    git fetch --all
    #    git reset --hard origin/$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')
    #    git pull
    #    $COMPOSE up -d
  elif [[ "$1" == "uninstall" ]]; then
    shift 1
    uninstall
  elif [[ "$1" == "reinstall" ]]; then
    shift 1
    uninstall
    sleep 3
    install
  elif [[ "$1" == "port" ]]; then
    shift 1
    env_set APP_PORT "$1"
    before_start
    $COMPOSE up -d
    check_run "修改"
  elif [[ "$1" == "nginx" ]]; then
    shift 1
    e="nginx $@" && run_exec nginx "$e"
  elif [[ "$1" == "mongo" ]]; then
    shift 1
    if [ "$1" = "dump" ]; then
      run_mongo dump
    elif [ "$1" = "restore" ]; then
      run_mongo restore
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
#echo "${OK} ${GreenBG} 安装完成 ${Font}"
#echo "地址: http://${GreenBG}127.0.0.1:$(env_get APP_PORT)${Font}"
