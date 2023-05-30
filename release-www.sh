#!/bin/bash
# version
if [ -n "$1" ]; then
  echo "version: $1"
else
  echo "需要带上版本号, 例如 sh release-www.sh 2.5.1"
  exit
fi
version=$1

nvm use v16.15.1

# jmal-cloud-view Directory location
view_dir="/Users/jmal/studio/myProject/github/jmal-cloud-view"
# jmal-cloud-server Directory location
server_dir="/Users/jmal/studio/myProject/github/jmal-cloud-server"

# build jmal-cloud-view
cd $view_dir || exit
echo "location: ${view_dir} "
if [ ! -f "dist-$version.tar" ]; then
  npm run build:prod
  tar -czf "dist-$version.tar" dist
fi
echo "current $(pwd)"
echo "copy dist-$version.tar to $server_dir/www/releases/dist-latest.tar"
cp "dist-$version.tar" $server_dir"/www/releases/dist-latest.tar"
