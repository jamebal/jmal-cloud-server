package com.jmal.clouddisk.listener;

import com.jmal.clouddisk.model.FilePropertie;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件变化监听器
 *
 * 在Apache的Commons-IO中有关于文件的监控功能的代码. 文件监控的原理如下：
 * 由文件监控类FileAlterationMonitor中的线程不停的扫描文件观察器FileAlterationObserver，
 * 如果有文件的变化，则根据相关的文件比较器，判断文件时新增，还是删除，还是更改。（默认为1000毫秒执行一次扫描）
 *
 *
 */
@Slf4j
@Service
public class FileListener extends FileAlterationListenerAdaptor {

    @Autowired
    FilePropertie filePropertie;

    /**
     * 文件创建执行
     */
    public void onFileCreate(File file) {
        String absolutePath = file.getAbsolutePath();
        String relativePath = absolutePath.replace(filePropertie.getRootDir() + File.separator,"");

        String[] relativePaths = relativePath.split(File.separator);
        if(relativePaths.length <= 1){
            return;
        }
        String username = relativePaths[0];

        log.info("用户:{},新建文件:{}",username,file.getAbsolutePath());
    }

    /**
     * 文件创建修改
     */
    public void onFileChange(File file) {
        log.info("[修改文件]:" + file.getAbsolutePath());
    }

    /**
     * 文件删除
     */
    public void onFileDelete(File file) {
        log.info("[删除文件]:" + file.getAbsolutePath());
    }

    /**
     * 目录创建
     */
    public void onDirectoryCreate(File directory) {
        log.info("[新建目录]:" + directory.getAbsolutePath());
    }

    /**
     * 目录修改
     */
    public void onDirectoryChange(File directory) {
        log.info("[修改目录]:" + directory.getAbsolutePath());
    }

    /**
     * 目录删除
     */
    public void onDirectoryDelete(File directory) {
        log.info("[删除目录]:" + directory.getAbsolutePath());
    }

    public void onStart(FileAlterationObserver observer) {
        super.onStart(observer);
    }

    public void onStop(FileAlterationObserver observer) {
        super.onStop(observer);
    }

}
