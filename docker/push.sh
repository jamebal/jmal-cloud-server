#!/bin/bash
# version
if [ -n "$1" ]; then
    echo "version: $1"
else
    echo "需要带上版本号, 例如 sh update.sh 2.1.8"
    exit
fi
version=$1
# jmal-cloud-view Directory location
view_dir="/Users/jmal/studio/myProject/github/jmal-cloud-view"
# jmal-cloud-server Directory location
server_dir="/Users/jmal/studio/myProject/github/jmal-cloud-server"
# docker aliyun registry password


# build jmal-cloud-view
cd $view_dir || exit
echo "location: ${view_dir} "
if [ ! -f "dist-$version.tar" ]; then
  npm run build:prod
  tar -czf "dist-$version.tar" dist
fi
cp "dist-$version.tar" $server_dir"/docker/"
cp "dist-$version.tar" $server_dir"/www/releases/dist-latest.tar"

# build jmal-cloud-server
cd $server_dir || exit
echo "location: ${server_dir} "
if [ ! -f "target/clouddisk-$version-exec.jar" ]; then
  mvn clean
  mvn -DskipTests=true package
fi
cp "target/clouddisk-$version-exec.jar" "docker/"

# build jmalcloud of Dockerfile
cd "$server_dir/docker" || exit
echo "location: ${server_dir}/docker "
docker build -t "jmalcloud:$version" --build-arg "version=$version" .
docker tag "jmalcloud:$version" "jmalcloud:latest"

pushAliYun() {
  echo "Push the image to the $1 ..."
  cat pwd.txt | docker login --username=bjmal --password-stdin "$1"
  docker tag "jmalcloud:$version" "$1/jmalcloud/jmalcloud:$version"
  docker tag "jmalcloud:$version" "$1/jmalcloud/jmalcloud:latest"
  docker push "$1/jmalcloud/jmalcloud:$version"
  docker push "$1/jmalcloud/jmalcloud:latest"
  removeLocalAliYunTag "$1"
}

pushDockerHub() {
  echo "Push the image to the DockerHub ..."
  docker tag "jmalcloud:$version" "jmal/jmalcloud:$version"
  docker tag "jmalcloud:$version" "jmal/jmalcloud:latest"
  docker push "jmal/jmalcloud:$version"
  docker push "jmal/jmalcloud:latest"
  removeDockerHub
}

removeDockerHub() {
  docker rmi "jmal/jmalcloud:$version"
  docker rmi "jmal/jmalcloud:latest"
  echo "removed the image DockerHub"
}

removeLocalAliYunTag() {
  docker rmi "$1/jmalcloud/jmalcloud:$version"
  docker rmi "$1/jmalcloud/jmalcloud:latest"
  echo "removed the image $1"
}

# Push the image to the registry
pushDockerHub
pushAliYun "registry.cn-guangzhou.aliyuncs.com"
pushAliYun "registry.cn-hangzhou.aliyuncs.com"
pushAliYun "registry.cn-chengdu.aliyuncs.com"
pushAliYun "registry.cn-beijing.aliyuncs.com"
exit 0;
