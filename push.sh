#!/usr/bin/env expect
set timeout 30
# 第一个参数 host
set host [lindex $argv 0]

# 第二个参数 user
set user [lindex $argv 1]

# 第三个参数 password
set password [lindex $argv 2]

# 第四个参数 upload_file
set upload_file [lindex $argv 3]

# 第五个参数 run_dir
set run_dir [lindex $argv 4]

# 第六个参数 version
set version [lindex $argv 6]

puts "host:$host\r\n user:$user\r\n password:$password\r\n upload_file:$upload_file\r\n run_dir:$run_dir\r\n version:$version\r\n"

# 上传文件
proc update_file {} {
  global host user password run_dir upload_file run_dir
  #上传
  spawn scp $upload_file $user@$host:$run_dir
  expect "*password:" { send "$password\r\n" }
  expect eof
}

# 重启
proc restart_jar {} {
    global host user password run_dir version
    #重启 jar
    global env
    set timeout -1
    spawn $env(SHELL)
    match_max 10
    send -- "ssh $user@$host\r"
    expect "*password*"
    send -- "$password\r"
    expect "*$user*"
    expect "#"
    send -- "cd $run_dir\r"
    expect "#"
    send -- "ps -ef | grep clouddisk-$version-exec.jar | grep java | awk '{printf(\"kill -15 %s\\n\",\$2)}' | sh \r"
    expect "#"
        send -- "nohup java -Xms50m -Xmx512m -jar clouddisk-$version-exec.jar --logging.level.root=warn --spring.profiles.active=prod --file.rootDir=/jmalcloud/ 2>&1 & \r"
    expect "#"
    send -- "exit\r"
    expect "#"
    send -- "\r"
}

puts "更新远程包..."
    update_file
    puts "重启..."
    restart_jar
