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
  tar -czf dist-$version.tar dist
fi
cp dist-$version.tar $server_dir"/docker/"

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
docker build -t jmalcloud:$version --build-arg version=$version .

pushDocker() {
  echo "Push the image to the $1 ..."
  docker login --username=bjmal --password=$password "$1"
  docker tag jmalcloud:$version "$1/jmalcloud/jmalcloud:$version"
  docker tag jmalcloud:$version "$1/jmalcloud/jmalcloud:latest"
  docker push "$1/jmalcloud/jmalcloud:$version"
  docker push "$1/jmalcloud/jmalcloud:latest"
  removeLocalTag "$1"
}

removeLocalTag() {
  docker rmi "$1/jmalcloud/jmalcloud:$version"
  docker rmi "$1/jmalcloud/jmalcloud:latest"
  echo "removed the image $1"
}

# Push the image to the registry
pushDocker "registry.cn-guangzhou.aliyuncs.com"
pushDocker "registry.cn-hangzhou.aliyuncs.com"
pushDocker "registry.cn-chengdu.aliyuncs.com"
pushDocker "registry.cn-beijing.aliyuncs.com"
exit 0;