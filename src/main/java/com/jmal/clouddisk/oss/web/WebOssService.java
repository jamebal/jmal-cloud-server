package com.jmal.clouddisk.oss.web;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.URLUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class WebOssService {

    @Autowired
    CommonFileService commonFileService;

    /***
     * 断点恢复上传缓存(已上传的分片缓存)
     * key: uploadId
     * value: 已上传的分片号列表
     */
    private static final Cache<String, CopyOnWriteArrayList<Integer>> listPartsCache = Caffeine.newBuilder().build();


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

    public ResponseResult<Object> searchFileAndOpenOssFolder(Path prePth, UploadApiParamDTO upload) {
        List<FileIntroVO> fileIntroVOList = getOssFileList(prePth, upload);
        ResponseResult<Object> result = ResultUtil.genResult();
        result.setCount(fileIntroVOList.size());
        result.setData(fileIntroVOList);
        return result;
    }

    public List<FileIntroVO> getOssFileList(Path prePth, UploadApiParamDTO upload) {
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
            // 排序
            fileIntroVOList = getSortFileList(upload, fileIntroVOList);
            // 分页
            if (upload.getPageIndex() != null && upload.getPageSize() != null) {
                fileIntroVOList = getPageFileList(fileIntroVOList, upload.getPageSize(), upload.getPageIndex());
            }
        }
        return fileIntroVOList;
    }

    private List<FileIntroVO> getSortFileList(UploadApiParamDTO upload, List<FileIntroVO> fileIntroVOList) {
        String order = upload.getOrder();
        if (!CharSequenceUtil.isBlank(order)) {
            String sortableProp = upload.getSortableProp();
            // 按文件大小排序
            if ("size".equals(sortableProp)) {
                if ("descending".equals(order)) {
                    // 倒序
                    fileIntroVOList = fileIntroVOList.stream().sorted(commonFileService::compareBySizeDesc).toList();
                } else {
                    // 正序
                    fileIntroVOList = fileIntroVOList.stream().sorted(commonFileService::compareBySize).toList();
                }
            }
            // 按文件最近修改时间排序
            if ("updateDate".equals(sortableProp)) {
                if ("descending".equals(order)) {
                    // 倒序
                    fileIntroVOList = fileIntroVOList.stream().sorted(commonFileService::compareByUpdateDateDesc).toList();
                } else {
                    // 正序
                    fileIntroVOList = fileIntroVOList.stream().sorted(commonFileService::compareByUpdateDate).toList();
                }
            }
        }
        // 默认按文件排序
        fileIntroVOList = commonFileService.sortByFileName(upload, fileIntroVOList, order);
        return fileIntroVOList;
    }

    public List<FileIntroVO> getPageFileList(List<FileIntroVO> fileIntroVOList, int pageSize, int pageIndex) {
        List<FileIntroVO> pageList = new ArrayList<>();
        int startIndex = (pageIndex - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, fileIntroVOList.size());
        for (int i = startIndex; i < endIndex; i++) {
            pageList.add(fileIntroVOList.get(i));
        }
        return pageList;
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
            // 已上传的分片号
            List<Integer> chunks = listPartsCache.get(uploadId, key -> ossService.getListParts(objectName, uploadId));
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

            // 已上传的分片号列表
            CopyOnWriteArrayList<Integer> chunks = listPartsCache.get(uploadId, key -> ossService.getListParts(objectName, uploadId));

            // 上传本次的分片
            ossService.uploadPart(file.getInputStream(), objectName,currentChunkSize, upload.getChunkNumber(), uploadId);

            // 加入缓存
            if (chunks != null) {
                chunks.add(upload.getChunkNumber());
                listPartsCache.put(uploadId, chunks);
                // 检测是否已经上传完了所有分片,上传完了则需要合并
                if (chunks.size() == upload.getTotalChunks()) {
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
