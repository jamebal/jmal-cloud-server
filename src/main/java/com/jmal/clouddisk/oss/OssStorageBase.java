package com.jmal.clouddisk.oss;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.thread.ThreadUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description OssStorageBase
 * @date 2023/4/3 16:33
 */
@Slf4j
public class OssStorageBase {

    private final FileProperties fileProperties;

    public static final Cache<String, List<String>> fileInfoListCache = Caffeine.newBuilder().initialCapacity(128).maximumSize(1024).expireAfterWrite(5, TimeUnit.SECONDS).build();
    public static final Cache<String, FileInfo> fileInfoCache = Caffeine.newBuilder().initialCapacity(128).maximumSize(1024).expireAfterWrite(5, TimeUnit.SECONDS).build();

    public static final Cache<String, Path> tempFileCache = Caffeine.newBuilder().build();

    public static final Cache<String, Set<String>> tempFileListCache = Caffeine.newBuilder().build();

    private final String bucketName;

    private final IOssStorageService ossStorageService;

    public OssStorageBase(IOssStorageService ossStorageService, String bucketName, FileProperties fileProperties) {
        this.ossStorageService = ossStorageService;
        this.bucketName = bucketName;
        this.fileProperties = fileProperties;
    }

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
        if (fileInfo == null) {
            Console.error("fileInfoCache.asMap()", fileInfoCache.asMap());
            Console.error("fileInfoListCache.asMap()", fileInfoListCache.asMap());
        }
        return fileInfo;
    }

    public static boolean deleteCache(String objectName) {
        Path path = getTempFileCache(objectName);
        if (path != null) {
            clearTempFileCache(objectName);
            return true;
        }
        return false;
    }

    public void afterDeleteCache(String objectName) {
        FileInfo fileInfo = getFileInfoCache(objectName);
        if (fileInfo != null) {
            OssStorageBase.fileInfoCache.invalidate(objectName);
            String key;
            if (fileInfo.isFolder()) {
                key = objectName.substring(0, objectName.length() - fileInfo.getName().length() - 1);
            } else {
                key = objectName.substring(0, objectName.length() - fileInfo.getName().length());
            }
            OssStorageBase.fileInfoListCache.invalidate(key);
        }
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

    public List<String> getFileNameList(String objectName) {
        List<String> fileNameList = OssStorageBase.fileInfoListCache.get(objectName, key -> {
            List<String> folderList = new ArrayList<>();
            List<FileInfo> fileInfos = ossStorageService.getFileInfoList(objectName);
            if (!fileInfos.isEmpty()) {
                for (FileInfo fileInfo : fileInfos) {
                    OssStorageBase.setFileInfoCache(fileInfo.getKey(), fileInfo);
                    folderList.add(fileInfo.getName());
                }
            }
            return folderList;
        });
        fileNameList = OssStorageBase.getTmepFileList(objectName, fileNameList);
        return fileNameList;
    }

    public static void setFileInfoCache(String key, FileInfo fileInfo) {
        if (key.length() > 1 && key.endsWith("/")) {
            key = key.substring(0, key.length() - 1);
        }
        fileInfoCache.put(key, fileInfo);
    }

    public FileInfo getFileInfoCache(String key) {
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

    public static List<String> getTmepFileList(String objectName, List<String> fileNameList) {
        if (fileNameList == null) {
            fileNameList = new ArrayList<>();
        }
        String objectParentName = objectName;
        if (objectParentName.endsWith("/")) {
            objectParentName = objectParentName.substring(0, objectParentName.length() - 1);
        }
        Set<String> nameList = OssStorageBase.getTempFileListCache(objectParentName);
        if (nameList != null) {
            fileNameList.addAll(nameList);
        }
        Console.error(objectParentName, "nameList", nameList);
        return fileNameList;
    }

    public boolean mkdir(String ossPath, String objectName) {
        // 临时目录绝对路径
        Path tempFileAbsolutePath = Paths.get(fileProperties.getRootDir(), ossPath, objectName);
        PathUtil.mkdir(tempFileAbsolutePath);
        setTempFileCache(objectName, tempFileAbsolutePath);
        // 稍后执行真正的mkdir
        ThreadUtil.execute(() -> ossStorageService.mkdir(objectName));
        return true;
    }

    public boolean writeObject(InputStream inputStream, String ossPath, String objectName) {
        Console.log("AliyunOSSStorageService", "writeObject", objectName);
        // 临时文件绝对路径
        Path tempFileAbsolutePath = Paths.get(fileProperties.getRootDir(), ossPath, objectName);
        try {
            Console.log("tempFileAbsolutePath", tempFileAbsolutePath);
            if (tempFileAbsolutePath.getNameCount() > 1) {
                Path parentPath = tempFileAbsolutePath.getParent();
                if (!Files.exists(parentPath)) {
                    PathUtil.mkdir(parentPath);
                }
            }
            Files.copy(inputStream, tempFileAbsolutePath, StandardCopyOption.REPLACE_EXISTING);
            setTempFileCache(objectName, tempFileAbsolutePath);
            // 稍后执行真正的上传
            ThreadUtil.execute(() -> ossStorageService.uploadFile(tempFileAbsolutePath, objectName));
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            return false;
        }
        Console.log("writeObject", true);
        return true;
    }

    public FileInfo newFileInfo(String objectName) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(0);
        fileInfo.setKey(objectName);
        fileInfo.setLastModified(new Date());
        fileInfo.setBucketName(bucketName);
        return fileInfo;
    }

    public FileInfo newFileInfo(String objectName, File file) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setSize(file.length());
        fileInfo.setKey(objectName);
        fileInfo.setLastModified(new Date(file.lastModified()));
        fileInfo.setBucketName(bucketName);
        return fileInfo;
    }

    public void setTempFileCache(String objectName, Path tempFileAbsolutePath) {
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

    @NotNull
    private static String getObjectParentName(String objectName) {
        Path path = Paths.get(objectName);
        String objectParentName = "";
        if (path.getNameCount() > 1) {
            objectParentName = path.getParent().toString();
        }
        return objectParentName;
    }

    public static Set<String> getTempFileListCache(String objectParentName) {
        return tempFileListCache.getIfPresent(objectParentName);
    }

    public static void setTempFileListCache(String objectParentName, Set<String> fileNameList) {
        tempFileListCache.put(objectParentName, fileNameList);
    }

    public static Path getTempFileCache(String objectName) {
        return tempFileCache.getIfPresent(objectName);
    }

    public static void clearTempFileCache(String objectName) {
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
        Console.log("tempFileCache", tempFileCache.asMap());
    }
}
