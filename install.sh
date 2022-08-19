#!/bin/bash

#fonts color
Red="\033[31m"
RedBG="\033[41;37m"
Font="\033[0m"

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

env_init() {
  if [[ "$(is_arm)" == "yes" ]]; then
    env_set OFFICE_IMAGE "onlyoffice/documentserver:latest-arm64"
  else
    env_set OFFICE_IMAGE "onlyoffice/documentserver:7.0.0.132"
  fi
  env_set APP_IPPR "10.$(rand 50 100).$(rand 100 200)"
}

########################################################################################################

# check_docker
check_docker

# env_init
env_init

# Initialization file
mkdir -p "${cur_path}/docker/www/"
tar -xzf "${cur_path}/www/releases/dist-latest.tar" -C "${cur_path}/docker/www/"

# Start the container
$COMPOSE up -d
