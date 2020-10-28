package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import com.jmal.clouddisk.model.FilePropertie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description 设置
 * @Date 2020/10/28 5:30 下午
 */
@Service
@Slf4j
public class SettingService {

    @Autowired
    FilePropertie filePropertie;

    @Autowired
    FileServiceImpl fileService;

    /***
     * 把文件同步到数据库
     * @param username 用户名
     */
    public void sync(String username) {
        Path path = Paths.get(filePropertie.getRootDir(),username);
        List<File> list = loopFiles(path.toFile());
        list.parallelStream().forEach(file -> fileService.createFile(username, file));
    }

    /***
     * 递归遍历目录以及子目录中的所有文件
     * @param file 当前遍历文件
     * @return 文件列表
     */
    public static List<File> loopFiles(File file) {
        final List<File> fileList = new ArrayList<>();
        if (null == file || !file.exists()) {
            return fileList;
        }
        fileList.add(file);
        if (file.isDirectory()) {
            final File[] subFiles = file.listFiles();
            if (ArrayUtil.isNotEmpty(subFiles)) {
                for (File tmp : subFiles) {
                    fileList.addAll(loopFiles(tmp));
                }
            }
        }
        return fileList;
    }

}
