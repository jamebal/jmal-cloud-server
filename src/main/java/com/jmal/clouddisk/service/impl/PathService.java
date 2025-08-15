package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.config.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class PathService {

    private final FileProperties fileProperties;

    /**
     * 通过文件的绝对路径获取用户名
     *
     * @param absolutePath 绝对路径
     * @return 用户名
     */
    public String getUsernameByAbsolutePath(Path absolutePath) {
        if (absolutePath == null) {
            return null;
        }
        Path parentPath = Paths.get(fileProperties.getRootDir());
        if (absolutePath.startsWith(parentPath)) {
            Path relativePath = parentPath.relativize(absolutePath);
            if (relativePath.getNameCount() > 1) {
                return relativePath.getName(0).toString();
            }
        }
        return null;
    }

    public String getRelativePath(String username, String fileAbsolutePath, String fileName) {
        int startIndex = fileProperties.getRootDir().length() + username.length() + 1;
        int endIndex = fileAbsolutePath.length() - fileName.length();
        if (startIndex >= endIndex) {
            return null;
        }
        return fileAbsolutePath.substring(startIndex, endIndex);
    }

    /**
     * 获取视频文件缓存目录
     *
     * @param username username
     * @param fileId   fileId
     * @return 视频文件缓存目录
     */
    public String getVideoCacheDir(String username, String fileId) {
        // 视频文件缓存目录
        String videoCacheDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileId).toString();
        if (!FileUtil.exist(videoCacheDir)) {
            FileUtil.mkdir(videoCacheDir);
        }
        return videoCacheDir;
    }

}
