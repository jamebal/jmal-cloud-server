### build
`docker build -t jmalcloud:latest .`

### docker images
`jmalcloud:latest` depend on `jmal/mongo:latest` -> `jmal/nginx:latest`

### run
docker run --name jmalcloud -p 7070:80 -p 7071:8080 -p 7072:8088 -v /Users/jmal/temp/jmalcloud-docker/files/:/jmalcloud/files/ -v /Users/jmal/temp/jmalcloud-docker/db/:/data/db/ -d jmalcloud:2.1
