### pre-build preparation

`npm run build:prod` jmal-cloud-view to dist-xxx.tar

`mvn package` jmal-cloud-server to clouddisk-xxx-exec.jar

copy `dist-xxx.tar` and `clouddisk-xxx-exec.jar` to the current directory

### build
`docker build -t jmalcloud:latest .`

### docker images dependencies
depend on `jmal/mongo:latest` depend on `jmal/nginx:latest`

### run
`docker run --restart=always --name jmalcloud -p 7070:80 -p 7071:8080 -p 7072:8088 -v /Users/jmal/temp/jmalcloud-docker/files/:/ jmalcloud/files/ -v /Users/jmal/temp/jmalcloud-docker/db/:/data/db/ -d registry.cn-guangzhou.aliyuncs.com/jmalcloud/jmalcloud: latest`
```
Start parameters description : 
Expose port : 
`80` : Web portal
`8080` : Blog entry
`8088` : Netdisk service entry
Disk mapping :
`/jmalcloud/files/` : Netdisk file storage directory
`/data/db/` : mongodb data storage directory
```