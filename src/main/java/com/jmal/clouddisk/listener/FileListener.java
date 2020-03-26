package com.jmal.clouddisk.listener;

import com.jmal.clouddisk.model.FilePropertie;
import com.jmal.clouddisk.service.IUploadFileService;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Autowired
    IUploadFileService fileService;

    /**
     * 文件创建执行
     */
    public void onFileCreate(File file) {
        try{
            String username = ownerOfChangeFile(file.getAbsolutePath());
            if(StringUtils.isEmpty(username)){
                return;
            }
            fileService.createFile(username,file);
//            log.info("用户:{},新建文件:{}",username,file.getAbsolutePath());
        }catch (Exception e){
            log.error("新建文件后续操作失败");
        }
    }

    /**
     * 文件创建修改
     */
    public void onFileChange(File file) {
        try{
            String username = ownerOfChangeFile(file.getAbsolutePath());
//            log.info("用户:{},修改文件:{}",username,file.getAbsolutePath());
        }catch (Exception e){
            log.error("修改文件后续操作失败");
        }
    }

    /**
     * 文件删除
     */
    public void onFileDelete(File file) {
        try{
            String username = ownerOfChangeFile(file.getAbsolutePath());
            if(StringUtils.isEmpty(username)){
                return;
            }
            fileService.deleteFile(username,file);
//            log.info("用户:{},删除文件:{}",username,file.getAbsolutePath());
        }catch (Exception e){
            log.error("删除文件后续操作失败");
        }
    }

    /**
     * 目录创建
     */
    public void onDirectoryCreate(File directory) {
        try{
            String username = ownerOfChangeFile(directory.getAbsolutePath());
            if(StringUtils.isEmpty(username)){
                return;
            }
            fileService.createFile(username,directory);
//            log.info("用户:{},新建目录:{}",username,directory.getAbsolutePath());
        }catch (Exception e){
            log.error("新建目录后续操作失败");
        }
    }

    /**
     * 目录修改
     */
    public void onDirectoryChange(File directory) {
        try{
            String username = ownerOfChangeFile(directory.getAbsolutePath());
//            log.info("用户:{},修改目录:{}",username,directory.getAbsolutePath());
        }catch (Exception e){
            log.error("修改目录后续操作失败");
        }
    }

    /**
     * 目录删除
     */
    public void onDirectoryDelete(File directory) {
        try{
            String username = ownerOfChangeFile(directory.getAbsolutePath());
            if(StringUtils.isEmpty(username)){
                return;
            }
            fileService.deleteFile(username,directory);
//            log.info("用户:{},删除目录:{}",username,directory.getAbsolutePath());
        }catch (Exception e){
            log.error("删除目录后续操作失败");
        }
    }

    public void onStart(FileAlterationObserver observer) {
        super.onStart(observer);
    }

    public void onStop(FileAlterationObserver observer) {
        super.onStop(observer);
    }

    /***
     * 判断变化的文件属于哪个用户
     * @return
     */
    private String ownerOfChangeFile(String fileAbsolutePath){
        try {
            String relativePath = fileAbsolutePath.replace(filePropertie.getRootDir() + File.separator,"");
            String[] relativePaths = relativePath.split(File.separator);
            if(relativePaths.length <= 1){
                return null;
            }
            return relativePaths[0];
        }catch (Exception e){
            log.error("解析路径失败,fileAbsolutePath:{}",fileAbsolutePath);
        }
        return null;
    }

}
