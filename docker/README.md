jmalcoud -> jmal/mongo -> jmal/nginx

docker run --name jmalcloud -p 19367:80 -p 14567:10010 -v /Users/jmal/temp/docker-file/:/jmalcloud/files/ -v /Users/jmal/data/db/:/data/db/ -d jmalcloud
