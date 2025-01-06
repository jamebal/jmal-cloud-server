#!/bin/bash

# 默认配置
# 文件分片大小(1MB = 1048576)
CHUNK_SIZE=1048576
# 1. 服务端地址，如果使用默认的docker-compose部署，则为: http://ip:7072， 或者 http://ip:7070/api
SERVER_URL=${SERVER_URL:-""}
# 2. 在设置-授权应用中添加一个访问令牌
ACCESS_TOKEN=${ACCESS_TOKEN:-""}
# 3. 用户账号，不是用户名
USERNAME=${USERNAME:-""}
# 4. 要上传到那个目录，根目录为"/"
CURRENT_DIRECTORY=${CURRENT_DIRECTORY:-""}

# 检查必要配置
check_config() {
    local missing_configs=()

    if [ -z "$SERVER_URL" ]; then
        missing_configs+=("SERVER_URL")
    fi

    if [ -z "$ACCESS_TOKEN" ]; then
        missing_configs+=("ACCESS_TOKEN")
    fi

    if [ -z "$USERNAME" ]; then
        missing_configs+=("USERNAME")
    fi

    if [ -z "$CURRENT_DIRECTORY" ]; then
        missing_configs+=("CURRENT_DIRECTORY")
    fi

    if [ ${#missing_configs[@]} -ne 0 ]; then
        echo "错误: 以下必要配置缺失:"
        printf '%s\n' "${missing_configs[@]}"
        echo
        echo "请通过以下方式之一进行配置:"
        echo "1. 设置环境变量:"
        for config in "${missing_configs[@]}"; do
            echo "   export $config=<value>"
        done
        echo
        echo "2. 直接修改脚本中的默认值:"
        for config in "${missing_configs[@]}"; do
            echo "   $config=\"<value>\""
        done
        exit 1
    fi

    # 验证服务器连接和访问令牌
    local response
    response=$(curl -s -w "\n%{http_code}" \
        --connect-timeout 3 \
        --max-time 3 \
        "${SERVER_URL}/api/user/info" \
        --header "access-token: ${ACCESS_TOKEN}")

    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')

    if [ "$http_code" != "200" ]; then
        echo "错误: 服务器连接失败"
        echo "请检查 SERVER_URL 是否正确"
        exit 1
    fi

    # 检查响应中的 code 字段
    if ! echo "$body" | grep -q '"code":0'; then
        echo "请检查 ACCESS_TOKEN 是否正确"
        exit 1
    fi
}

# 检查命令行参数
if [ $# -ne 1 ]; then
    echo "用法: $0 <文件或目录路径>"
    exit 1
fi

PATH_TO_UPLOAD="$1"

# 检查路径是否存在
if [ ! -e "$PATH_TO_UPLOAD" ]; then
    echo "错误: 路径不存在 - $PATH_TO_UPLOAD"
    exit 1
fi

# 检查配置
check_config


# URL编码函数
url_encode() {
    local string="$1"
    echo -n "$string" | xxd -p | sed 's/\(..\)/%\1/g'
}

# 获取文件大小的函数，适配 Linux 和 macOS
get_file_size() {
    local file_path="$1"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        stat -f%z "$file_path"
    else
        # Linux
        stat -c%s "$file_path"
    fi
}

# 进度条
show_progress() {
    local progress=$1
    local width=50
    local percent=$((progress * width / 100))
    printf "\r"
    printf "上传进度: ["
    for ((i=0; i<width; i++)); do
        if [ $i -lt $percent ]; then
            printf "#"
        else
            printf " "
        fi
    done
    printf "] %3d%%" $progress
}

# 文件上传
upload_chunk() {
    local chunk_file="$1"
    local chunk_number="$2"
    local current_chunk_size="$3"

    # URL编码文件名和路径
    local encoded_filename=$(url_encode "$FILE_NAME")
    local encoded_relativepath=$(url_encode "$FILE_NAME")
    local encoded_directory=$(url_encode "$CURRENT_DIRECTORY")

    local check_url="${SERVER_URL}/upload?chunkNumber=${chunk_number}&chunkSize=${CHUNK_SIZE}&currentChunkSize=${current_chunk_size}&totalSize=${TOTAL_SIZE}&identifier=${IDENTIFIER}&filename=${encoded_filename}&relativePath=${encoded_relativepath}&totalChunks=${TOTAL_CHUNKS}&isFolder=false&currentDirectory=${encoded_directory}&username=${USERNAME}"

    local check_response=$(curl -s -H "access-token: ${ACCESS_TOKEN}" -H "lang: zh-CN" "$check_url")

    if echo "$check_response" | grep -q '"upload":true'; then
        curl -s --location --request POST "${SERVER_URL}/upload" \
        --header "access-token: ${ACCESS_TOKEN}" \
        --form "chunkNumber=$chunk_number" \
        --form "chunkSize=$CHUNK_SIZE" \
        --form "currentChunkSize=$current_chunk_size" \
        --form "totalSize=$TOTAL_SIZE" \
        --form "identifier=$IDENTIFIER" \
        --form "filename=$encoded_filename" \
        --form "relativePath=$encoded_relativepath" \
        --form "totalChunks=$TOTAL_CHUNKS" \
        --form "isFolder=false" \
        --form "currentDirectory=$encoded_directory" \
        --form "username=$USERNAME" \
        --form "file=@$chunk_file"
        if [ $? -ne 0 ]; then
            return 1
        fi
    fi
    return 0
}

# 文件夹上传
upload_directory() {
    local dir_path="$1"
    local base_dir="$2"
    local original_current_directory="$CURRENT_DIRECTORY"
    local dir_name=$(basename "$dir_path")

    local total_files=$(find "$dir_path" -type f | wc -l)
    local current_file=0

    echo "开始上传文件夹: $dir_name (共 $total_files 个文件)"

    find "$dir_path" -type f | while read file_path; do
        current_file=$((current_file + 1))

        local rel_path=${file_path#$base_dir/}
        local file_dir=$(dirname "$rel_path")

        # 设置并编码当前目录路径
        if [ "$file_dir" = "." ]; then
            CURRENT_DIRECTORY="${original_current_directory}/${dir_name}"
        else
            CURRENT_DIRECTORY="${original_current_directory}/${file_dir}"
        fi

        echo "正在上传 ($current_file/$total_files): $(basename "$file_path") -> ${CURRENT_DIRECTORY}"

        FILE_PATH="$file_path"
        FILE_NAME=$(basename "$file_path")
        TOTAL_SIZE=$(get_file_size "$FILE_PATH")
        IDENTIFIER="${TOTAL_SIZE}-${FILE_NAME}"
        IDENTIFIER=$(echo "$IDENTIFIER" | tr -cd '[:alnum:]-')

        # URL编码相关变量
        local encoded_filename=$(url_encode "$FILE_NAME")
        local encoded_directory=$(url_encode "$CURRENT_DIRECTORY")

        if [ $TOTAL_SIZE -le $CHUNK_SIZE ]; then
            TOTAL_CHUNKS=1
            upload_chunk "$FILE_PATH" 1 "$TOTAL_SIZE" > /dev/null
            if [ $? -ne 0 ]; then
                echo "文件上传失败: $FILE_NAME"
                continue
            fi
        else
            TOTAL_CHUNKS=$(( ($TOTAL_SIZE + $CHUNK_SIZE - 1) / $CHUNK_SIZE ))
            TEMP_DIR=$(mktemp -d)
            split -b $CHUNK_SIZE "$FILE_PATH" "$TEMP_DIR/chunk."

            CHUNK_INDEX=1
            for CHUNK_FILE in "$TEMP_DIR"/chunk.*; do
                CURRENT_CHUNK_SIZE=$(get_file_size "$CHUNK_FILE")
                upload_chunk "$CHUNK_FILE" "$CHUNK_INDEX" "$CURRENT_CHUNK_SIZE" > /dev/null
                if [ $? -ne 0 ]; then
                    echo "分片上传失败: $FILE_NAME"
                    rm -rf "$TEMP_DIR"
                    continue 2
                fi
                CHUNK_INDEX=$((CHUNK_INDEX + 1))
            done

            rm -rf "$TEMP_DIR"

            # 合并文件时使用编码后的参数
            MERGE_URL="${SERVER_URL}/merge"
            MERGE_PARAMS="filename=${encoded_filename}&relativePath=${encoded_filename}&identifier=${IDENTIFIER}&currentDirectory=${encoded_directory}&username=${USERNAME}&totalSize=${TOTAL_SIZE}&isFolder=false"

            MERGE_RESPONSE=$(curl -s -X POST "${MERGE_URL}?${MERGE_PARAMS}" \
            -H "access-token: ${ACCESS_TOKEN}" \
            -H "lang: zh-CN")

            if ! echo "$MERGE_RESPONSE" | grep -q '"code":0'; then
                echo "文件合并失败: $FILE_NAME"
                echo "错误响应: $MERGE_RESPONSE"
                continue
            fi
        fi
        echo "文件上传完成: $FILE_NAME"
    done

    CURRENT_DIRECTORY="$original_current_directory"
}

if [ -d "$PATH_TO_UPLOAD" ]; then
    ABSOLUTE_PATH=$(cd "$(dirname "$PATH_TO_UPLOAD")" && pwd)
    upload_directory "$PATH_TO_UPLOAD" "$ABSOLUTE_PATH"
    echo "文件夹上传完成"
else
    FILE_PATH="$PATH_TO_UPLOAD"
    FILE_NAME=$(basename "$FILE_PATH")
    TOTAL_SIZE=$(get_file_size "$FILE_PATH")
    IDENTIFIER="${TOTAL_SIZE}-${FILE_NAME}"
    IDENTIFIER=$(echo "$IDENTIFIER" | tr -cd '[:alnum:]-')

    echo "开始上传文件: $FILE_NAME (大小: $TOTAL_SIZE 字节)"

    if [ $TOTAL_SIZE -le $CHUNK_SIZE ]; then
        # 小文件直接上传
        TOTAL_CHUNKS=1
        upload_chunk "$FILE_PATH" 1 "$TOTAL_SIZE" > /dev/null
        if [ $? -ne 0 ]; then
            echo "文件上传失败"
            exit 1
        fi
    else
        # 大文件分片上传
        TOTAL_CHUNKS=$(( ($TOTAL_SIZE + $CHUNK_SIZE - 1) / $CHUNK_SIZE ))
        TEMP_DIR=$(mktemp -d)
        split -b $CHUNK_SIZE "$FILE_PATH" "$TEMP_DIR/chunk."

        echo "开始分片上传..."
        CHUNK_INDEX=1
        for CHUNK_FILE in "$TEMP_DIR"/chunk.*; do
            CURRENT_CHUNK_SIZE=$(get_file_size "$CHUNK_FILE")
            PROGRESS=$((CHUNK_INDEX * 100 / TOTAL_CHUNKS))
            show_progress $PROGRESS

            upload_chunk "$CHUNK_FILE" "$CHUNK_INDEX" "$CURRENT_CHUNK_SIZE" > /dev/null
            if [ $? -ne 0 ]; then
                echo -e "\n分片上传失败"
                rm -rf "$TEMP_DIR"
                exit 1
            fi
            CHUNK_INDEX=$((CHUNK_INDEX + 1))
        done

        rm -rf "$TEMP_DIR"
        echo -e "\n分片上传完成"

        # 合并文件
        echo "开始合并文件..."

        # URL编码文件名和目录
        encoded_filename=$(url_encode "$FILE_NAME")
        encoded_directory=$(url_encode "$CURRENT_DIRECTORY")

        MERGE_URL="${SERVER_URL}/merge"
        MERGE_PARAMS="filename=${encoded_filename}&relativePath=${encoded_filename}&identifier=${IDENTIFIER}&currentDirectory=${encoded_directory}&username=${USERNAME}&totalSize=${TOTAL_SIZE}&isFolder=false"

        MERGE_RESPONSE=$(curl -s -X POST "${MERGE_URL}?${MERGE_PARAMS}" \
        -H "access-token: ${ACCESS_TOKEN}" \
        -H "lang: zh-CN")

        if ! echo "$MERGE_RESPONSE" | grep -q '"code":0'; then
            echo "文件合并失败"
            echo "错误响应: $MERGE_RESPONSE"
            exit 1
        fi
    fi

    echo "文件上传完成"
fi

