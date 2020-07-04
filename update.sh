#!/bin/bash

# 远程服务器信息
host=34.92.198.20
user=root
password=wigojmal
run_dir=/root/jmalcloud/

if [[ ! -n "$1" ]] ;then
  version=1.0
else
  version=$1
fi

# 要上传的文件
upload_file=target/clouddisk-$version-exec.jar

mvn clean

mvn package -Dmaven.test.skip=true

if [ ! -f "$upload_file" ]; then
    echo "没有找到文件：$upload_file"
    exit;
else
    touch "$upload_file"
fi

expect push.sh ${host} ${user} ${password} ${upload_file} ${run_dir} ${version}
