package com.jmal.clouddisk.oss;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.FileProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description OssStorageBase
 * @date 2023/4/3 16:33
 */
@Slf4j
public class BaseOssService {

    private final FileProperties fileProperties;
    /**
     * 目录下文件名列表缓存 </br>
     * key: objectName </br>
     * value: object下的文件名列表 </br>
     */
    private final Cache<String, List<String>> fileInfoListCache;
    /**
     * object缓存 </br>
     * key: objectName </br>
     * value: FileInfo </br>
     */
    private final Cache<String, FileInfo> fileInfoCache;
    /**
     * 临时文件缓存 </br>
     * key: objectName </br>
     * value: 临时文件绝对路径 </br>
     */
    private final Cache<String, Path> tempFileCache;
    /**
     * 临时文件列表缓存 </br>
     * key: objectName </br>
     * value: object下的文件名列表 </br>
     */
    private final Cache<String, Set<String>> tempFileListCache;

    /**
     * 等待上传文件的缓存 </br>
     * key: objectName </br>
     * value: 临时文件绝对路径 </br>
     */
    private final Cache<String, Path> waitingUploadCache;

    private final String bucketName;

    private final IOssService ossService;

    public BaseOssService(IOssService ossService, String bucketName, FileProperties fileProperties, ScheduledThreadPoolExecutor scheduledThreadPoolExecutor) {
        this.ossService = ossService;
        this.bucketName = bucketName;
        this.fileProperties = fileProperties;
        scheduledThreadPoolExecutor.scheduleWithFixedDelay(this::checkUpload, 1, 1, TimeUnit.SECONDS);
        this.fileInfoListCache = Caffeine.newBuilder().initialCapacity(128).maximumSize(1024).expireAfterWrite(5, TimeUnit.HOURS).build();
        this.fileInfoCache = Caffeine.newBuilder().initialCapacity(128).maximumSize(1024).expireAfterWrite(5, TimeUnit.HOURS).build();
        this.tempFileCache = Caffeine.newBuilder().build();
        this.tempFileListCache = Caffeine.newBuilder().build();
        this.waitingUploadCache = Caffeine.newBuilder().build();
    }

    /**
     * 根据objectName获取FileInfo
     * @param objectName objectName
     * @return FileInfo
     */
    public FileInfo getFileInfo(String objectName) {
        FileInfo fileInfo = getFileInfoCache(objectName);
        if (fileInfo == null) {
            String path = getObjectParentName(objectName);
            refresh(path);
        }
        fileInfo = getFileInfoCache(objectName);
        if (fileInfo == null) {
            refresh(objectName);
        }
        fileInfo = getFileInfoCache(objectName);
        return fileInfo;
    }

    public boolean delete(String objectName) {
        boolean deleted;
        deleteTempFileCache(objectName);
        if (objectName.endsWith("/")) {
            // 删除目录
            deleted = ossService.deleteDir(objectName);
        } else {
            // 删除文件
            deleted = ossService.deleteObject(objectName);
        }
        if (deleted) {
            onDeleteSuccess(objectName);
        }
        return deleted;
    }

    public boolean mkdir(String objectName) {
        FileInfo fileInfo = ossService.newFolder(objectName);
        if (fileInfo != null) {
            onMkdirSuccess(objectName, fileInfo);
        }
        return fileInfo != null;
    }

    /**
     * 上传对象到临时文件
     * @param inputStream 网络输入流
     * @param ossPath     oss路径前缀, 例如：/username/aliyunoss
     * @param objectName  objectName
     * @return 写入临时文件是否成功
     */
    public boolean writeTempFile(InputStream inputStream, String ossPath, String objectName) {
        // 临时文件绝对路径
        Path tempFileAbsolutePath = Paths.get(fileProperties.getRootDir(), ossPath, objectName);
        try {
            if (tempFileAbsolutePath.getNameCount() > 1) {
                Path parentPath = tempFileAbsolutePath.getParent();
                if (!Files.exists(parentPath)) {
                    PathUtil.mkdir(parentPath);
                }
            }
            Files.copy(inputStream, tempFileAbsolutePath, StandardCopyOption.REPLACE_EXISTING);
            setTempFileCache(objectName, tempFileAbsolutePath);
            setWaitingUploadCache(objectName, tempFileAbsolutePath);
            // 稍后执行真正的上传, 在 checkUpload 方法里
            return true;
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取文件名列表
     * @param objectName objectName
     * @return object下的文件名列表
     */
    public List<String> getFileNameList(String objectName) {
        List<String> fileNameList = fileInfoListCache.get(objectName, key -> {
            List<String> folderList = new ArrayList<>();
            List<FileInfo> fileInfos = ossService.getFileInfoList(objectName);
            if (fileInfos != null && !fileInfos.isEmpty()) {
                for (FileInfo fileInfo : fileInfos) {
                    setFileInfoCache(fileInfo.getKey(), fileInfo);
                    folderList.add(fileInfo.getName());
                }
            }
            return folderList;
        });
        fileNameList = getTmepFileNameList(objectName, fileNameList);
        return fileNameList;
    }

    public AbstractOssObject getObject(String objectName) {
        Path path = getTempFileCache(objectName);
        if (path != null) {
            return new TempFileObject(path.toFile(), objectName, bucketName);
        }
        printOperation(ossService.getPlatform().getKey(), "getObject", objectName);
        return ossService.getAbstractOssObject(objectName);
    }

    /**
     * 删除临时文件缓存
     * @param objectName objectName
     */
    private void deleteTempFileCache(String objectName) {
        Path path = getTempFileCache(objectName);
        if (path != null) {
            clearTempFileCache(objectName);
        }
    }

    /**
     * 删除oss文件成功后，要干的事
     * @param objectName objectName
     */
    private void onDeleteSuccess(String objectName) {
        log.info(objectName, "delete success");
        FileInfo fileInfo = getFileInfoCache(objectName);
        if (fileInfo != null) {
            fileInfoCache.invalidate(objectName);
            String key;
            if (fileInfo.isFolder()) {
                key = objectName.substring(0, objectName.length() - fileInfo.getName().length() - 1);
            } else {
                key = objectName.substring(0, objectName.length() - fileInfo.getName().length());
            }
            fileInfoListCache.invalidate(key);
        }
    }

    /**
     * 上传至oos成功后，要干的事
     * @param objectName objectName
     * @param tempFileAbsolutePath 临时文件绝对路径
     */
    public void onUploadSuccess(String objectName, Path tempFileAbsolutePath) {
        log.info(objectName, "upload success");
        clearTempFileCache(objectName);
        setFileInfoCache(objectName, newFileInfo(objectName, tempFileAbsolutePath.toFile()));
        removeWaitingUploadCache(objectName);
    }

    /**
     * 在oss创建目录成功后，要干的事
     * @param objectName objectName
     * @param fileInfo FileInfo
     */
    private void onMkdirSuccess(String objectName, FileInfo fileInfo) {
        log.info(objectName, "mkdir success");
        setFileInfoCache(objectName, fileInfo);
        fileInfoListCache.invalidateAll();
    }

    /**
     * 刷新缓存
     * @param objectName objectName
     */
    private void refresh(String objectName) {
        if (objectName.equals("/")) {
            getFileNameList(objectName);
        } else {
            if (objectName.length() > 1) {
                getFileNameList(objectName + "/");
            } else {
                getFileNameList(objectName);
            }
        }
    }

    /**
     * 获取临时文件名列表
     * @param objectName objectName
     * @param fileNameList object下的文件名列表
     * @return object下的文件名列表 + object下的临时文件名列表
     */
    private List<String> getTmepFileNameList(String objectName, List<String> fileNameList) {
        if (fileNameList == null) {
            fileNameList = new ArrayList<>();
        }
        String objectParentName = objectName;
        if (objectParentName.endsWith("/")) {
            objectParentName = objectParentName.substring(0, objectParentName.length() - 1);
        }
        Set<String> nameList = getTempFileListCache(objectParentName);
        if (nameList != null) {
            fileNameList.addAll(nameList);
        }
        return fileNameList;
    }

    /**
     * <p>检查是否有需要上传的文件 </p>
     * 有等待上传的文件且文件的最后修改时间大于5秒就上传 <br/>
     * 该方法每秒执行一次 <br/>
     */
    private void checkUpload() {
        getWaitingUploadCacheMap().forEach((objectName, tempFileAbsolutePath) -> {
            long lastModified = tempFileAbsolutePath.toFile().lastModified();
            // 临时文件的最后修改时间大于5秒就上传
            if ((System.currentTimeMillis() - lastModified) > 5000) {
                removeWaitingUploadCache(objectName);
                ThreadUtil.execute(() -> ossService.uploadFile(tempFileAbsolutePath, objectName));
            }
        });
    }

    public void addFileInfoList(String objectName, List<FileInfo> fileInfoList, S3ObjectSummary s3ObjectSummary) {
        if (!s3ObjectSummary.getKey().equals(objectName)) {
            FileInfo fileInfo = getFileInfo(s3ObjectSummary);
            fileInfoList.add(fileInfo);
        } else {
            FileInfo fileInfo = getFileInfoCache(objectName);
            if (fileInfo == null) {
                setFileInfoCache(objectName, newFileInfo(objectName));
            }
        }
    }

    private FileInfo getFileInfo(S3ObjectSummary objectSummary) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(objectSummary.getSize());
        fileInfo.setKey(objectSummary.getKey());
        fileInfo.setETag(objectSummary.getETag());
        fileInfo.setLastModified(objectSummary.getLastModified());
        fileInfo.setBucketName(objectSummary.getBucketName());
        return fileInfo;
    }

    public FileInfo newFileInfo(String objectName) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(0);
        fileInfo.setKey(objectName);
        fileInfo.setLastModified(new Date());
        fileInfo.setBucketName(bucketName);
        return fileInfo;
    }

    private FileInfo newFileInfo(String objectName, File file) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(file.length());
        fileInfo.setKey(objectName);
        fileInfo.setLastModified(new Date(file.lastModified()));
        fileInfo.setBucketName(bucketName);
        return fileInfo;
    }

    /**
     * 获取object的父级object
     * @param objectName objectName
     * @return 父级objectName, 结尾不带"/"
     */
    private String getObjectParentName(String objectName) {
        Path path = Paths.get(objectName);
        String objectParentName = "";
        if (path.getNameCount() > 1) {
            objectParentName = path.getParent().toString();
        }
        return objectParentName;
    }

    private void setFileInfoCache(String key, FileInfo fileInfo) {
        if (key.length() > 1 && key.endsWith("/")) {
            key = key.substring(0, key.length() - 1);
        }
        fileInfoCache.put(key, fileInfo);
    }

    private FileInfo getFileInfoCache(String key) {
        if (key.length() > 1 && key.endsWith("/")) {
            key = key.substring(0, key.length() - 1);
        }
        FileInfo fileInfo = fileInfoCache.getIfPresent(key);
        if (fileInfo == null) {
            Path path = getTempFileCache(key);
            if (path != null) {
                fileInfo = newFileInfo(key, path.toFile());
            }
        }
        return fileInfo;
    }

    public void printOperation(String platform, String operation, String objectName) {
        log.info("{}, {}, {}", platform, operation, objectName);
    }

    /**
     * 设置临时文件缓存
     * @param objectName objectName
     * @param tempFileAbsolutePath 临时文件绝对路径
     */
    private void setTempFileCache(String objectName, Path tempFileAbsolutePath) {
        tempFileCache.put(objectName, tempFileAbsolutePath);
        // setTempFileListCache
        String objectParentName = getObjectParentName(objectName);
        Set<String> fileNameList = getTempFileListCache(objectParentName);
        if (fileNameList == null) {
            fileNameList = new HashSet<>();
        }
        fileNameList.add(tempFileAbsolutePath.getFileName().toString());
        setTempFileListCache(objectParentName, fileNameList);
    }


    /**
     * 清理临时文件缓存
     * @param objectName objectName
     */
    private void clearTempFileCache(String objectName) {
        Path path = getTempFileCache(objectName);
        if (path != null) {
            tempFileCache.invalidate(objectName);
            try {
                // 删除临时文件
                Files.delete(path);
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
            }
        }
        String objectParentName = getObjectParentName(objectName);
        Set<String> fileNameList = getTempFileListCache(objectParentName);
        if (fileNameList != null) {
            String objectFileName = Paths.get(objectName).getFileName().toString();
            if (fileNameList.contains(objectFileName)) {
                fileNameList.remove(objectFileName);
                setTempFileListCache(objectParentName, fileNameList);
            }
            if (fileNameList.isEmpty()) {
                tempFileListCache.invalidate(objectParentName);
            }
        }
    }

    private Set<String> getTempFileListCache(String objectParentName) {
        return tempFileListCache.getIfPresent(objectParentName);
    }

    private void setTempFileListCache(String objectParentName, Set<String> fileNameList) {
        tempFileListCache.put(objectParentName, fileNameList);
    }

    private Path getTempFileCache(String objectName) {
        return tempFileCache.getIfPresent(objectName);
    }

    private void setWaitingUploadCache(String objectName, Path tempFileAbsolutePath) {
        waitingUploadCache.put(objectName, tempFileAbsolutePath);
    }

    private Map<String, Path> getWaitingUploadCacheMap() {
        return waitingUploadCache.asMap();
    }

    private void removeWaitingUploadCache(String objectName) {
        waitingUploadCache.invalidate(objectName);
    }
}
