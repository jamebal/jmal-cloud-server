package com.jmal.clouddisk.oss.web;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.UploadResponse;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class WebOssService {

    @Autowired
    CommonFileService commonFileService;


    public static String getObjectName(Path prePath, String ossPath, boolean isFolder) {
        String name = "";
        int ossPathCount = Paths.get(ossPath).getNameCount();
        if (prePath.getNameCount() > ossPathCount) {
            name = prePath.subpath(ossPathCount, prePath.getNameCount()).toString();
            if (!name.endsWith("/") && isFolder) {
                name = name + "/";
            }
        }
        return URLUtil.decode(name);
    }

    public ResponseResult<Object> searchFileAndOpenOssFolder(Path prePth) {
        List<FileIntroVO> fileIntroVOList = getOssFileList(prePth);
        ResponseResult<Object> result = ResultUtil.genResult();
        result.setCount(fileIntroVOList.size());
        result.setData(fileIntroVOList);
        return result;
    }

    public static List<FileIntroVO> getOssFileList(Path prePth) {
        List<FileIntroVO> fileIntroVOList = new ArrayList<>();
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath == null) {
            return fileIntroVOList;
        }
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, true);
        List<FileInfo> list = ossService.getFileInfoList(objectName);
        if (!list.isEmpty()) {
            fileIntroVOList = list.stream().map(fileInfo -> fileInfo.toFileIntroVO(ossPath)).toList();
        }
        return fileIntroVOList;
    }

    private static String getOssRootFolderName(String ossPath) {
        return CaffeineUtil.getOssDiameterPrefixCache(ossPath).getFolderName();
    }

    public Optional<FileIntroVO> readToText(String ossPath, Path prePth) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        try (AbstractOssObject abstractOssObject = ossService.getObject(objectName);
             InputStream inputStream = abstractOssObject.getInputStream()) {
            FileIntroVO fileIntroVO = new FileIntroVO();
            FileInfo fileInfo = abstractOssObject.getFileInfo();
            String context;
            if (fileInfo != null && inputStream != null) {
                fileIntroVO = fileInfo.toFileIntroVO(ossPath);
                context = IoUtil.read(inputStream, StandardCharsets.UTF_8);
                fileIntroVO.setContentText(context);
            }
            return Optional.of(fileIntroVO);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    public UploadResponse checkChunk(String ossPath, Path prePth, UploadApiParamDTO upload) {
        UploadResponse uploadResponse = new UploadResponse();
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        if (ossService.doesObjectExist(objectName)) {
            // 对象已存在
            uploadResponse.setPass(true);
        } else {
            String uploadId = ossService.getUploadId(objectName);
            List<Integer> chunks = ossService.getListParts(objectName, uploadId);
            // 返回已存在的分片
            uploadResponse.setResume(chunks);
            assert chunks != null;
            if (upload.getTotalChunks() == chunks.size()) {
                // 文件不存在,并且已经上传了所有的分片,则合并保存文件
                ossService.completeMultipartUpload(objectName, uploadId);
                notifyCreateFile(upload.getUsername(), objectName, getOssRootFolderName(ossPath));
            }
        }
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    public UploadResponse mergeFile(String ossPath, Path prePth, UploadApiParamDTO upload) {
        UploadResponse uploadResponse = new UploadResponse();

        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        ossService.completeMultipartUpload(objectName, ossService.getUploadId(objectName));
        notifyCreateFile(upload.getUsername(), objectName, getOssRootFolderName(ossPath));
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    public UploadResponse upload(String ossPath, Path prePth, UploadApiParamDTO upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();

        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);

        int currentChunkSize = upload.getCurrentChunkSize();
        long totalSize = upload.getTotalSize();
        MultipartFile file = upload.getFile();
        if (currentChunkSize == totalSize) {
            // 没有分片,直接存
            ossService.uploadFile(file.getInputStream(), objectName, currentChunkSize);
            notifyCreateFile(upload.getUsername(), objectName, getOssRootFolderName(ossPath));
        } else {
            // 上传分片
            String uploadId = ossService.getUploadId(objectName);
            ossService.uploadPart(file.getInputStream(), objectName,currentChunkSize, upload.getChunkNumber(), uploadId);

            // 检测是否已经上传完了所有分片,上传完了则需要合并
            if (Objects.equals(upload.getChunkNumber(), upload.getTotalChunks())) {
                List<Integer> chunks = ossService.getListParts(objectName, uploadId);
                Console.log("已经上传完了所有分片?");
                if (chunks.size() == upload.getTotalChunks()) {
                    Console.log("已经上传完了所有分片");
                    uploadResponse.setMerge(true);
                }
            }
        }
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    private void notifyCreateFile(String username, String objectName, String ossRootFolderName) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        fileIntroVO.setPath(getPathByObjectName(ossRootFolderName, objectName));
        Console.log("fileIntroVO", fileIntroVO);
        commonFileService.pushMessage(username, fileIntroVO, "createFile");
    }

    private static String getPathByObjectName(String ossRootFolderName, String objectName) {
        String path = MyWebdavServlet.PATH_DELIMITER;
        Path filePath = Paths.get(ossRootFolderName, objectName);
        if (filePath.getNameCount() > 1) {
            path += filePath.getParent().toString() + MyWebdavServlet.PATH_DELIMITER;
        }
        return path;
    }

    public void delete(String ossPath, List<String> objectNameList) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        for (String objectName : objectNameList) {
            ossService.delete(objectName);
        }
    }
}
