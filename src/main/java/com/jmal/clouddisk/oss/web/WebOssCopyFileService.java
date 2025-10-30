package com.jmal.clouddisk.oss.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.CommonUserFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.SimplePathVisitor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebOssCopyFileService {

    private final WebOssCommonService webOssCommonService;
    private final CommonFileService commonFileService;
    private final CommonUserFileService commonUserFileService;
    private final FileProperties fileProperties;
    private final IFileDAO fileDAO;

    /**
     * 从 oss 复制 到 oss
     *
     * @param ossPathFrom               源ossPath
     * @param sourceObjectNamePath      源objectPath
     * @param ossPathTo                 目标ossPath
     * @param destinationObjectNamePath 目标objectPath
     * @param isMove                    是否为移动操作
     */
    public ResponseResult<Object> copyOssToOss(String ossPathFrom, String sourceObjectNamePath, String ossPathTo, String destinationObjectNamePath, boolean isMove) {

        IOssService ossServiceFrom = OssConfigService.getOssStorageService(ossPathFrom);
        IOssService ossServiceTo = OssConfigService.getOssStorageService(ossPathTo);

        BucketInfo bucketInfoFrom = CaffeineUtil.getOssDiameterPrefixCache(ossPathFrom);
        BucketInfo bucketInfoTo = CaffeineUtil.getOssDiameterPrefixCache(ossPathTo);

        String objectNameFrom = sourceObjectNamePath.substring(ossPathFrom.length());

        boolean isFolder = objectNameFrom.endsWith("/");
        String objectNameTo;
        Path objectNameFromPath = Paths.get(objectNameFrom);
        objectNameTo = destinationObjectNamePath.substring(ossPathTo.length()) + objectNameFromPath.getFileName();
        if (objectNameFrom.isEmpty()) {
            isFolder = true;
            objectNameTo = destinationObjectNamePath.substring(ossPathTo.length()) + Paths.get(ossPathFrom).getFileName().toString();
        }
        if (isFolder) {
            objectNameTo += MyWebdavServlet.PATH_DELIMITER;
        }

        // 判断目标文件/夹是否存在
        if (ossServiceTo.doesObjectExist(objectNameTo)) {
            return ResultUtil.warning(Constants.COPY_EXISTS_FILE);
        }

        if (ossServiceFrom.getPlatform() == ossServiceTo.getPlatform()) {
            // 同平台间复制
            List<String> copiedList = ossServiceFrom.copyObject(bucketInfoFrom.getBucketName(), objectNameFrom, bucketInfoTo.getBucketName(), objectNameTo);
            for (String objectName : copiedList) {
                webOssCommonService.afterUploadComplete(objectName, ossPathTo, null);
            }
            if (copiedList.isEmpty()) {
                throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "复制失败");
            }
        } else {
            if (isFolder) {
                // 复制文件夹
                copyDir(ossServiceFrom, ossServiceTo, objectNameFrom, objectNameTo, ossPathTo);
            } else {
                // 复制文件
                copyFile(ossServiceFrom, ossServiceTo, objectNameFrom, objectNameTo, ossPathTo);
            }
        }
        String finalObjectNameTo = objectNameTo;
        // 复制成功
        ossServiceTo.clearCache(finalObjectNameTo);
        webOssCommonService.notifyCreateFile(WebOssCommonService.getUsernameByOssPath(ossPathTo), finalObjectNameTo, WebOssCommonService.getOssRootFolderName(ossPathTo));
        String operation = isMove ? "移动" : "复制";
        Path fromPath = Paths.get(WebOssCommonService.getOssRootFolderName(ossPathFrom), objectNameFrom);
        Path toPath = Paths.get(WebOssCommonService.getOssRootFolderName(ossPathTo), finalObjectNameTo);
        commonFileService.pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), WebOssCommonService.getUsernameByOssPath(ossPathFrom), operation);
        return ResultUtil.success();
    }

    /**
     * 不同oss平台间的文件夹复制
     * 复制完成需要解锁 源objectName
     *
     * @param ossServiceFrom 源ossService
     * @param ossServiceTo   目标ossService
     * @param objectNameFrom 源objectName
     * @param objectNameTo   目标objectName
     * @param ossPathTo      目标ossPath
     */
    private void copyDir(IOssService ossServiceFrom, IOssService ossServiceTo, String objectNameFrom, String objectNameTo, String ossPathTo) {
        // 锁对象
        ossServiceFrom.lock(objectNameFrom);
        try {
            // 首先在目标oss创建文件夹
            if (ossServiceTo.mkdir(objectNameTo)) {
                // 列出源objectName下的所有文件/文件夹
                List<FileInfo> fileInfoList = ossServiceFrom.getAllObjectsWithPrefix(objectNameFrom);
                // 先创建文件夹
                fileInfoList.stream().filter(FileInfo::isFolder).parallel().forEach(fileInfo -> {
                    String relativePath = fileInfo.getKey().substring(objectNameFrom.length());
                    // 目标objectName
                    String destObjectName = objectNameTo + relativePath;
                    ossServiceTo.mkdir(destObjectName);
                    webOssCommonService.afterUploadComplete(destObjectName, ossPathTo, null);
                });
                // 再复制文件
                fileInfoList.stream().filter(fileInfo -> !fileInfo.isFolder()).parallel().forEach(fileInfo -> {
                    String relativePath = fileInfo.getKey().substring(objectNameFrom.length());
                    // 目标objectName
                    String destObjectName = objectNameTo + relativePath;
                    try (AbstractOssObject abstractOssObject = ossServiceFrom.getAbstractOssObject(fileInfo.getKey());
                         InputStream inputStream = abstractOssObject.getInputStream()) {
                        // 上传文件
                        ossServiceTo.uploadFile(inputStream, destObjectName, abstractOssObject.getContentLength());
                        webOssCommonService.afterUploadComplete(destObjectName, ossPathTo, null);
                    } catch (Exception e) {
                        throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        } finally {
            // 解锁对象
            ossServiceFrom.unlock(objectNameFrom);
        }
    }

    /**
     * 不同oss平台间的文件复制
     * 复制完成需要解锁 源objectName
     *
     * @param ossServiceFrom 源ossService
     * @param ossServiceTo   目标ossService
     * @param objectNameFrom 源objectName
     * @param objectNameTo   目标objectName
     * @param ossPathTo      目标ossPath
     */
    private void copyFile(IOssService ossServiceFrom, IOssService ossServiceTo, String objectNameFrom, String objectNameTo, String ossPathTo) {
        // 锁对象
        ossServiceFrom.lock(objectNameFrom);
        try (AbstractOssObject abstractOssObject = ossServiceFrom.getAbstractOssObject(objectNameFrom);
             InputStream inputStream = abstractOssObject.getInputStream()) {
            // 上传文件
            ossServiceTo.uploadFile(inputStream, objectNameTo, abstractOssObject.getContentLength());
            webOssCommonService.afterUploadComplete(objectNameTo, ossPathTo, null);
        } catch (Exception e) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        } finally {
            // 解锁对象
            ossServiceFrom.unlock(objectNameFrom);
        }
    }

    /**
     * 从oss复制文件/夹到本地存储
     *
     * @param ossPathFrom          源ossPath
     * @param sourceObjectNamePath 源objectPath
     * @param fileId               目标文件fileId
     * @param isMove               是否为移动操作
     */
    public ResponseResult<Object> copyOssToLocal(String ossPathFrom, String sourceObjectNamePath, String fileId, boolean isMove) {
        IOssService ossServiceFrom = OssConfigService.getOssStorageService(ossPathFrom);
        String objectNameFrom = sourceObjectNamePath.substring(ossPathFrom.length());
        boolean isFolder = objectNameFrom.endsWith("/");

        FileBaseDTO destFileDocument;
        if (Constants.REGION_DEFAULT.equals(fileId)) {
            // 根目录
            destFileDocument = new FileBaseDTO();
            destFileDocument.setName("");
            destFileDocument.setPath("/");
        } else {
            destFileDocument = fileDAO.findFileBaseDTOById(fileId);
            if (destFileDocument == null) {
                throw new CommonException(ExceptionType.DIR_NOT_FIND);
            }
        }
        destFileDocument.setUsername(WebOssCommonService.getUsernameByOssPath(ossPathFrom));
        if (isFolder) {
            // 复制文件夹
            copyDir(ossServiceFrom, objectNameFrom, destFileDocument);
        } else {
            // 复制文件
            copyFile(ossServiceFrom, objectNameFrom, destFileDocument);
        }
        String operation = isMove ? "移动" : "复制";
        Path fromPath = Paths.get(WebOssCommonService.getOssRootFolderName(ossPathFrom), objectNameFrom);
        Path toPath = Paths.get(destFileDocument.getPath(), destFileDocument.getName(), Paths.get(objectNameFrom).getFileName().toString());
        commonFileService.pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), destFileDocument.getUsername(), operation);
        return ResultUtil.success();
    }

    /**
     * 从oss复制文件夹到本地
     *
     * @param ossServiceFrom   源ossService
     * @param objectNameFrom   源objectName
     * @param destFileDocument 目标fileDocument
     */
    private void copyDir(IOssService ossServiceFrom, String objectNameFrom, FileBaseDTO destFileDocument) {
        // 锁对象
        ossServiceFrom.lock(objectNameFrom);
        try {
            String destDir = Paths.get(fileProperties.getRootDir(), destFileDocument.getUsername(), destFileDocument.getPath(), destFileDocument.getName()).toString();
            String destDirPath = Paths.get(destDir, Paths.get(objectNameFrom).getFileName().toString()).toString();
            // 首先创建文件夹
            FileUtil.mkdir(destDirPath);
            // 列出源objectName下的所有文件/文件夹
            List<FileInfo> fileInfoList = ossServiceFrom.getAllObjectsWithPrefix(objectNameFrom);
            // 先创建文件夹
            fileInfoList.stream().filter(FileInfo::isFolder).parallel().forEach(fileInfo -> {
                String relativePath = fileInfo.getKey().substring(objectNameFrom.length());
                // 目标目录
                Path destPath = Paths.get(destDirPath, relativePath);
                PathUtil.mkdir(destPath);
                commonUserFileService.createFile(destFileDocument.getUsername(), destPath.toFile(), null, null);
            });
            // 再复制文件
            fileInfoList.stream().filter(fileInfo -> !fileInfo.isFolder()).parallel().forEach(fileInfo -> {
                String relativePath = fileInfo.getKey().substring(objectNameFrom.length());
                // 目标objectName
                try (AbstractOssObject abstractOssObject = ossServiceFrom.getAbstractOssObject(fileInfo.getKey());
                     InputStream inputStream = abstractOssObject.getInputStream()) {
                    // 目标文件
                    File destFile = Paths.get(destDirPath, relativePath).toFile();
                    FileUtil.writeFromStream(inputStream, destFile);
                    commonUserFileService.createFile(destFileDocument.getUsername(), destFile, null, null);
                } catch (Exception e) {
                    throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
                }
            });
        } catch (Exception e) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        } finally {
            // 解锁对象
            ossServiceFrom.unlock(objectNameFrom);
        }
    }

    /**
     * 从oss复制文件到本地
     *
     * @param ossServiceFrom   源ossService
     * @param objectNameFrom   源objectName
     * @param destFileDocument 目标fileDocument
     */
    private void copyFile(IOssService ossServiceFrom, String objectNameFrom, FileBaseDTO destFileDocument) {
        // 锁对象
        ossServiceFrom.lock(objectNameFrom);
        try (AbstractOssObject abstractOssObject = ossServiceFrom.getAbstractOssObject(objectNameFrom);
             InputStream inputStream = abstractOssObject.getInputStream()) {
            // 上传文件
            Path destPath = Paths.get(fileProperties.getRootDir(), destFileDocument.getUsername(), destFileDocument.getPath(), destFileDocument.getName());
            PathUtil.mkdir(destPath);
            // 目标文件
            File destFile = Paths.get(destPath.toString(), Paths.get(objectNameFrom).getFileName().toString()).toFile();
            FileUtil.writeFromStream(inputStream, destFile);
            commonUserFileService.createFile(destFileDocument.getUsername(), destFile, null, null);
        } catch (Exception e) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        } finally {
            // 解锁对象
            ossServiceFrom.unlock(objectNameFrom);
        }
    }

    /**
     * 从本地存储复制文件/夹到oss
     *
     * @param ossPathTo          目标ossPath
     * @param fileId             源fileId
     * @param destObjectNamePath 目标objectNamePath
     * @param isMove             是否为移动操作
     */
    public ResponseResult<Object> copyLocalToOss(String ossPathTo, String fileId, String destObjectNamePath, boolean isMove) {
        IOssService ossServiceTo = OssConfigService.getOssStorageService(ossPathTo);
        FileBaseDTO fromFileDocument = fileDAO.findFileBaseDTOById(fileId);
        if (fromFileDocument == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        boolean isFolder = fromFileDocument.getIsFolder();
        fromFileDocument.setUsername(WebOssCommonService.getUsernameByOssPath(ossPathTo));
        // 目标objectName
        String objectNameTo = destObjectNamePath.substring(ossPathTo.length()) + fromFileDocument.getName();
        // 判断目标文件/夹是否存在
        if (ossServiceTo.doesObjectExist(objectNameTo)) {
            return ResultUtil.warning(Constants.COPY_EXISTS_FILE);
        }
        if (isFolder) {
            // 复制文件夹
            copyDir(fromFileDocument, ossServiceTo, objectNameTo, ossPathTo);
        } else {
            // 复制文件
            copyFile(fromFileDocument, ossServiceTo, objectNameTo, ossPathTo);
        }
        if (isMove) {
            // 删除源文件
            Path fromPathFile = Paths.get(fileProperties.getRootDir(), fromFileDocument.getUsername(), fromFileDocument.getPath(), fromFileDocument.getName());
            FileUtil.del(fromPathFile);
        }
        Completable.fromAction(() -> {
                    TimeUnit.SECONDS.sleep(1);
                    webOssCommonService.notifyCreateFile(fromFileDocument.getUsername(), objectNameTo, WebOssCommonService.getOssRootFolderName(ossPathTo));
                    Path fromPath = Paths.get(fromFileDocument.getPath(), fromFileDocument.getName());
                    Path toPath = Paths.get(WebOssCommonService.getOssRootFolderName(ossPathTo), objectNameTo);
                    commonFileService.pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), fromFileDocument.getUsername(), isMove ? "移动" : "复制");
                })
                .subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success();
    }

    /**
     * 从本地复制文件夹到oss
     *
     * @param fromFileDocument 源FileDocument
     * @param ossServiceTo     目标ossService
     * @param objectNameTo     目标objectName
     * @param ossPathTo        目标ossPath
     */
    private void copyDir(FileBaseDTO fromFileDocument, IOssService ossServiceTo, String objectNameTo, String ossPathTo) {
        // 锁文件
        CommonFileService.lockFile(fromFileDocument);
        Path fromPath = Paths.get(fileProperties.getRootDir(), fromFileDocument.getUsername(), fromFileDocument.getPath(), fromFileDocument.getName());
        try {
            // 首先在目标oss创建文件夹
            if (ossServiceTo.mkdir(objectNameTo)) {
                // 遍历fromPath下的所有目录和文件上传至目标oss
                PathUtil.walkFiles(fromPath, new SimplePathVisitor() {
                    @NotNull
                    @Override
                    public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                        if (!dir.equals(fromPath)) {
                            String objectName = objectNameTo + dir.toString().substring(fromPath.toString().length());
                            ossServiceTo.mkdir(objectName);
                            webOssCommonService.afterUploadComplete(objectName, ossPathTo, null);
                        }
                        return super.preVisitDirectory(dir, attrs);
                    }

                    @NotNull
                    @Override
                    public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                        String objectName = objectNameTo + file.toString().substring(fromPath.toString().length());
                        File fromFile = file.toFile();
                        try (InputStream inputStream = new FileInputStream(fromFile)) {
                            ossServiceTo.uploadFile(inputStream, objectName, fromFile.length());
                            webOssCommonService.afterUploadComplete(objectName, ossPathTo, null);
                        } catch (Exception e) {
                            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
                        }
                        return super.visitFile(file, attrs);
                    }
                });
            }
        } catch (Exception e) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        } finally {
            // 解锁文件
            CommonFileService.unLockFile(fromFileDocument);
        }
    }

    /**
     * 从本地复制文件到oss
     *
     * @param fromFileDocument 源FileDocument
     * @param ossServiceTo     目标ossService
     * @param objectNameTo     目标objectName
     * @param ossPathTo        目标ossPath
     */
    private void copyFile(FileBaseDTO fromFileDocument, IOssService ossServiceTo, String objectNameTo, String ossPathTo) {
        // 锁文件
        CommonFileService.lockFile(fromFileDocument);
        // 上传文件
        File fromFile = Paths.get(fileProperties.getRootDir(), fromFileDocument.getUsername(), fromFileDocument.getPath(), fromFileDocument.getName()).toFile();
        try (InputStream intStream = new FileInputStream(fromFile)) {
            ossServiceTo.uploadFile(intStream, objectNameTo, fromFile.length());
            webOssCommonService.afterUploadComplete(objectNameTo, ossPathTo, null);
        } catch (Exception e) {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        } finally {
            // 解锁文件
            CommonFileService.unLockFile(fromFileDocument);
        }
    }
}
