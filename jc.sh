#!/bin/bash

#fonts color
Green="\033[32m"
Red="\033[31m"
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
    echo -e "${Error} ${Red}未安装 Docker！${Font}"
    exit 1
  fi
  if ! docker ps > /dev/null 2>&1; then
    echo -e "${Error} ${Red}请确保您有操作docker的权限。${Font}"
    exit 1
  fi
  docker-compose version &>/dev/null
  if [ $? -ne 0 ]; then
    docker compose version &>/dev/null
    if [ $? -ne 0 ]; then
      echo -e "${Error} ${Red}未安装 Docker-compose！${Font}"
      exit 1
    fi
    COMPOSE="docker compose"
  fi
  if [[ -n $($COMPOSE version | grep -E "\sv*1") ]]; then
    $COMPOSE version
    echo -e "${Error} ${Red}Docker-compose 的版本太低了，请升级到 v2+！${Font}"
    exit 1
  fi
  if [ ! -f docker-compose.yml ]; then
      cp docker-compose.example.yml docker-compose.yml
  fi
}

run_exec() {
  local container=$1
  local cmd=$2
  local name="$(env_get CONTAINER_NAME_PREFIX)_$container"
  if [ -z "$name" ]; then
    echo -e "${Error} ${Red}没有找到 $container 容器!${Font}"
    exit 1
  fi
  docker exec -it "$name" /bin/sh -c "$cmd"
}

judge() {
  if [[ 0 -eq $? ]]; then
    echo -e "${OK} ${Green}$1 完成${Font}"
    sleep 1
  else
    echo -e "${Error} ${Red}$1 失败${Font}"
    exit 1
  fi
}

rand() {
  local min=$1
  local max=$(($2 - $min + 1))
  local num=$(($RANDOM + 1000000000))
  echo $(($num % $max + $min))
}

local_ipv4() {
  local ip="127.0.0.1"
  if [[ $(uname) == 'Linux' ]]; then
    ip=$(ip a | grep inet | grep -v 127.0.0.1 | grep -v inet6 | awk '{print $2}' | tr -d "addr:" | head -n 1)
  fi
  if [[ $(uname) == 'Darwin' ]]; then
    ip=$(ifconfig -a | grep inet | grep -v 127.0.0.1 | grep -v inet6 | awk '{print $2}' | tr -d "addr:" | head -n 1)
  fi
  echo "${ip%/*}"
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
      echo -e "${Error} ${Red}设置env参数失败!${Font}"
      exit 1
    fi
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
    pull                 拉取最新的的程序和镜像
    update               更新
    start                启动
    stop                 停止
    restart              重启
    mongo                执行容器 mongodb 容器内的命令 example: ./jc.sh mongo mongostat
    nginx                执行容器 nginx 容器内的命令   example: ./jc.sh nginx nginx -v
    dump                 备份数据库
    restore              恢复数据库
    reinstall            卸载后再安装
    reset-password       重置初始管理员的密码
"
}

env_init() {
  env_set OFFICE_IMAGE_VERSION "7.0.0.132"
  env_set DRAWIO_IMAGE_VERSION "20.2.3"
  env_set APP_PORT 7070
  env_set BLOG_PORT 7071
  env_set SERVER_PORT 7072
  env_set FTP_SERVER_PORT 7073
  env_set CONTAINER_NAME_PREFIX "jmalcloud"
  local file_ptah=$(cat ${cur_path}/.env | grep "^RESOURCE_DB_PATH=")
  if [ -z "$file_ptah" ]; then
    env_set RESOURCE_DB_PATH "$cur_path/docker/jmalcloud/mongodb"
  fi
  local db_ptah=$(cat ${cur_path}/.env | grep "^RESOURCE_FILE_PATH=")
  if [ -z "$db_ptah" ]; then
    env_set RESOURCE_FILE_PATH "$cur_path/docker/jmalcloud/files"
  fi
}

before_start() {
  [[ "$(arg_get port)" -gt 0 ]] && env_set APP_PORT "$(arg_get port)"
  [[ "$(arg_get blog_port)" -gt 0 ]] && env_set BLOG_PORT "$(arg_get blog_port)"
  [[ "$(arg_get server_port)" -gt 0 ]] && env_set SERVER_PORT "$(arg_get server_port)"
  [[ "$(arg_get ftp_server_port)" -gt 0 ]] && env_set FTP_SERVER_PORT "$(arg_get ftp_server_port)"
  [[ "$(arg_get prefix)" -gt 0 ]] && env_set CONTAINER_NAME_PREFIX "$(arg_get prefix)"
}

install() {
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
    echo -e "${Red} 开始卸载... ${Font}"
    ;;
  *)
    echo -e "${OK} ${Green} 终止卸载 ${Font}"
    exit 2
    ;;
  esac
  $COMPOSE down
  rm -rf "./docker"
  echo -e "${OK} ${Green} 卸载完成 ${Font}"
}

check_run() {
  spin=('\' '|' '/' '-')
  server_url="http://$(local_ipv4):$(env_get BLOG_PORT)"
  for ((i = 0; i <= 350; i++)); do
    url_status=$(curl -s -m 5 -IL "$server_url" | grep 200)
    if [ "$url_status" != "" ]; then
      echo -ne "                                                              \r"
      echo -e "${OK} ${Green}$1完成${Font}"
      echo "网盘地址: http://$(local_ipv4):$(env_get APP_PORT)"
      echo "博客地址: $server_url"
      echo "API 地址: http://$(local_ipv4):$(env_get SERVER_PORT)/public/api"
      break
    else
      echo -n "正在启动服务, 请稍等... ${spin[$((i % 4))]}"
      echo -n -e \\r
      sleep 0.2
    fi
  done
  url_status=$(curl -s -m 5 -IL $server_url | grep 200)
  if [ "$url_status" == "" ]; then
    echo -e "${Error} ${Red}$1失败，反馈给开发者: https://github.com/jamebal/jmal-cloud-view/issues${Font}"
  fi
}

pull() {
  git fetch --all
  git reset --hard origin/$(git branch | sed -n -e 's/^\* \(.*\)/\1/p')
  git pull origin master
  $COMPOSE pull
}

backup_and_install() {
  local container_mongodb="$(env_get CONTAINER_NAME_PREFIX)_mongodb"
  if docker ps --format '{{.Names}}' | grep -q "$container_mongodb"; then
    run_mongo "dump"
    run_exec nginx "nginx -s reload"
    install
  else
    install
  fi
}

reset_password() {
  local container_server="$(env_get CONTAINER_NAME_PREFIX)_server"
  echo -e "正在重置密码..."
  docker restart "$container_server" > /dev/null 2>&1
  run_exec mongodb "mongo jmalcloud --eval \"db.getCollection('user').update({ 'creator': true }, {\\\$set: { 'password': '1000:c5b705ea13a1221f5e59110947ed806f8a978e955fbd2ed6:22508de12228c34a235454a0caf3bcaa5552858543258e56' }}, { 'multi': false, 'upsert': false })\" > /dev/null 2>&1"
  check_run "重置"
  echo -e "${OK} ${Green}重置后的密码为：jmalcloud${Font}"
}

########################################################################################################
check_docker
env_init

if [ $# -gt 0 ]; then
  if [[ "$1" == "init" ]] || [[ "$1" == "install" ]]; then
    shift 1
    install
  elif [[ "$1" == "pull" ]]; then
    shift 1
    pull
  elif [[ "$1" == "update" ]]; then
    shift 1
    backup_and_install
  elif [[ "$1" == "uninstall" ]]; then
    shift 1
    uninstall
  elif [[ "$1" == "reinstall" ]]; then

    shift 1
    uninstall
    sleep 3
    install
  elif [[ "$1" == "reset-password" ]]; then
    shift 1
    reset_password
  elif [[ "$1" == "port" ]]; then
    shift 1
    env_set APP_PORT "$1"
    before_start
    $COMPOSE up -d
    check_run "修改"
  elif [[ "$1" == "nginx" ]]; then
    shift 1
    e="$@" && run_exec nginx "$e"
  elif [[ "$1" == "mongo" ]]; then
    shift 1
    e="$@" && run_exec mongodb "$e"
  elif [[ "$1" == "dump" ]]; then
    shift 1
    run_mongo "dump"
  elif [[ "$1" == "restore" ]]; then
    shift 1
    run_mongo "restore"
  elif [[ "$1" == "start" ]]; then
    shift 1
    # 启动容器
    before_start
    $COMPOSE start "$@"
  elif [[ "$1" == "stop" ]]; then
    shift 1
    $COMPOSE stop "$@"
  elif [[ "$1" == "restart" ]]; then
    shift 1
    $COMPOSE stop "$@"
    # 启动容器
    before_start
    $COMPOSE start "$@"
    # 检测服务是否启动
    check_run "启动"
  else
    if [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]] || [[ "$1" == "man" ]]; then
      help
    else
      echo "$1 is not a support parameter."
    fi
  fi
else
  help
fi
