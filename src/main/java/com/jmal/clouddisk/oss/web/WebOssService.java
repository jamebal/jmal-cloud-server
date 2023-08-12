package com.jmal.clouddisk.oss.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.SecureUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.oss.*;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebOssService extends WebOssCommonService {


    private final IFileVersionService fileVersionService;

    private final UserLoginHolder userLoginHolder;

    /***
     * 断点恢复上传缓存(已上传的分片缓存)
     * key: uploadId
     * value: 已上传的分片号列表
     */
    private static final Cache<String, CopyOnWriteArrayList<Integer>> LIST_PARTS_CACHE = Caffeine.newBuilder().build();

    public List<Map<String, String>> getPlatformList() {
        List<Map<String, String>> maps = new ArrayList<>(PlatformOSS.values().length);
        for (PlatformOSS platformOSS : PlatformOSS.values()) {
            Map<String, String> map = new HashMap<>(2);
            map.put("value", platformOSS.getKey());
            map.put("label", platformOSS.getValue());
            maps.add(map);
        }
        return maps;
    }

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
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath == null) {
            return ResultUtil.success().setData(new ArrayList<>(0)).setCode(0);
        }
        return getOssFileList(ossPath, prePth, upload);
    }

    public ResponseResult<Object> getOssFileList(String ossPath, Path prePth, UploadApiParamDTO upload) {
        ResponseResult<Object> result = ResultUtil.genResult();
        result.setData(Collections.emptyList());
        result.setCount(0);
        List<FileIntroVO> fileIntroVOList;
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, true);
        List<FileInfo> list = ossService.getFileInfoListCache(objectName);
        if (!list.isEmpty()) {
            String userId = null;
            if (upload != null) {
                userId = upload.getUserId();
            }
            if (CharSequenceUtil.isNotBlank(userId)) {
                userId = userService.getUserIdByUserName(getUsernameByOssPath(ossPath));
            }

            List<FileDocument> fileDocumentList = getFileDocuments(ossPath, objectName);
            String finalUserId = userId;
            //过滤条件
            list = filterOther(upload, list);
            result.setCount(list.size());
            //配置属性
            fileIntroVOList = list.stream().map(fileInfo -> {
                FileIntroVO fileIntroVO = fileInfo.toFileIntroVO(ossPath, finalUserId);
                if (upload != null && BooleanUtil.isTrue(upload.getPathAttachFileName())) {
                    fileIntroVO.setPath(fileIntroVO.getPath() + fileIntroVO.getName());
                }
                // 设置文件的额外属性:分享属性和收藏属性
                FileDocument fileDocument = fileDocumentList.stream().filter(f -> f.getId().equals(fileIntroVO.getId())).findFirst().orElse(null);
                if (fileDocument != null) {
                    fileIntroVO.setIsFavorite(fileDocument.getIsFavorite());
                    fileIntroVO.setShareBase(fileDocument.getShareBase());
                    fileIntroVO.setExpiresAt(fileDocument.getExpiresAt());
                    fileIntroVO.setIsShare(fileDocument.getIsShare());
                }
                return fileIntroVO;
            }).toList();
            // 排序
            fileIntroVOList = getSortFileList(upload, fileIntroVOList);
            // 分页
            if (upload != null && upload.getPageIndex() != null && upload.getPageSize() != null) {
                fileIntroVOList = getPageFileList(fileIntroVOList, upload.getPageSize(), upload.getPageIndex());
            }
            result.setData(fileIntroVOList);
        }
        return result;
    }

    /**
     * 查询object目录下是否有额外属性,有就返回List<FileDocument>
     *
     * @param ossPath    ossPath
     * @param objectName objectName必须为目录
     * @return List<FileDocument>
     */
    private List<FileDocument> getFileDocuments(String ossPath, String objectName) {
        if (objectName.length() > 0 && !objectName.endsWith("/")) {
            return new ArrayList<>();
        }
        Query query = new Query();
        String parentName = Paths.get(objectName).getFileName().toString();
        String path = getPath(objectName, getOssRootFolderName(ossPath));
        if (parentName.length() > 0) {
            path += parentName + MyWebdavServlet.PATH_DELIMITER;
        }
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.find(query, FileDocument.class);
    }

    private static List<FileInfo> filterOther(UploadApiParamDTO upload, List<FileInfo> fileInfoList) {
        if (upload != null && BooleanUtil.isTrue(upload.getJustShowFolder())) {
            // 只显示文件夹
            return fileInfoList.stream().filter(FileInfo::isFolder).toList();
        }
        return fileInfoList;
    }

    private List<FileIntroVO> getSortFileList(UploadApiParamDTO upload, List<FileIntroVO> fileIntroVOList) {
        if (upload == null) {
            return fileIntroVOList;
        }
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

    public Optional<FileIntroVO> readToText(String ossPath, Path prePth, Boolean content) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        try (AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName);
             InputStream inputStream = abstractOssObject.getInputStream()) {
            FileIntroVO fileIntroVO = new FileIntroVO();
            FileInfo fileInfo = abstractOssObject.getFileInfo();
            if (fileInfo != null && inputStream != null) {
                String userId = userService.getUserIdByUserName(getUsernameByOssPath(ossPath));
                fileIntroVO = fileInfo.toFileIntroVO(ossPath, userId);
                if (BooleanUtil.isTrue(content)) {
                    String context = IoUtil.read(inputStream, StandardCharsets.UTF_8);
                    fileIntroVO.setContentText(context);
                }
            }
            return Optional.of(fileIntroVO);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    public StreamingResponseBody readToTextStream(String ossPath, Path prePth) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        return outputStream -> {
            try (AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName);
                 InputStream inputStream = abstractOssObject.getInputStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                    outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        };
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
            List<Integer> chunks = LIST_PARTS_CACHE.get(uploadId, key -> ossService.getListParts(objectName, uploadId));
            // 返回已存在的分片
            uploadResponse.setResume(chunks);
            assert chunks != null;
            if (upload.getTotalChunks() == chunks.size()) {
                // 文件不存在,并且已经上传了所有的分片,则合并保存文件
                ossService.completeMultipartUpload(objectName, uploadId, upload.getTotalSize());
                // 清除缓存
                removeListPartsCache(uploadId);
                notifyCreateFile(upload.getUsername(), objectName, getOssRootFolderName(ossPath));
                afterUploadComplete(objectName, ossPath, upload);
            }
        }
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    public UploadResponse mergeFile(String ossPath, Path prePth, UploadApiParamDTO upload) {
        UploadResponse uploadResponse = new UploadResponse();

        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        String uploadId = ossService.getUploadId(objectName);
        ossService.completeMultipartUpload(objectName, ossService.getUploadId(objectName), upload.getTotalSize());
        // 清除缓存
        removeListPartsCache(uploadId);
        notifyCreateFile(upload.getUsername(), objectName, getOssRootFolderName(ossPath));
        afterUploadComplete(objectName, ossPath, upload);
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    /**
     * 清除分片缓存
     *
     * @param uploadId uploadId
     */
    private static void removeListPartsCache(String uploadId) {
        LIST_PARTS_CACHE.invalidate(uploadId);
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
            try (InputStream inputStream = file.getInputStream()) {
                ossService.uploadFile(inputStream, objectName, currentChunkSize);
            }
            notifyCreateFile(upload.getUsername(), objectName, getOssRootFolderName(ossPath));
            afterUploadComplete(objectName, ossPath, upload);
        } else {
            // 上传分片
            String uploadId = ossService.getUploadId(objectName);

            // 已上传的分片号列表
            CopyOnWriteArrayList<Integer> chunks = LIST_PARTS_CACHE.get(uploadId, key -> ossService.getListParts(objectName, uploadId));

            // 上传本次的分片
            boolean success = ossService.uploadPart(file.getInputStream(), objectName, currentChunkSize, upload.getChunkNumber(), uploadId);
            if (!success) {
                // 上传分片失败
                uploadResponse.setUpload(false);
                return uploadResponse;
            }

            // 加入缓存
            if (chunks != null) {
                chunks.add(upload.getChunkNumber());
                LIST_PARTS_CACHE.put(uploadId, chunks);
                // 检测是否已经上传完了所有分片,上传完了则需要合并
                if (chunks.size() == upload.getTotalChunks()) {
                    uploadResponse.setMerge(true);
                }
            }
        }
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    public void afterUploadComplete(String objectName, String ossPath, UploadApiParamDTO upload) {
        Query query = new Query();
        String username = getUsernameByOssPath(ossPath);
        String rootName = getOssRootFolderName(ossPath);
        query.addCriteria(Criteria.where("_id").is(getFileId(rootName, objectName, username)));
        Update update = new Update();
        FileInfo fileInfo = new FileInfo(objectName, upload.getIdentifier(), upload.getTotalSize(), new Date());
        FileDocument fileDocument = fileInfo.toFileDocument(ossPath, upload.getUserId());
        commonFileService.checkShareBase(update, getPath(objectName, rootName));
        Document updateObject = update.getUpdateObject();
        if (updateObject.get("$set") != null) {
            Document document = updateObject.get("$set", Document.class);
            if (document.get(Constants.IS_SHARE) != null) {
                fileDocument.setIsShare(document.getBoolean(Constants.IS_SHARE));
            }
            if (document.get(Constants.SHARE_ID) != null) {
                fileDocument.setShareId(document.getString(Constants.SHARE_ID));
            }
            if (document.get(Constants.EXPIRES_AT) != null) {
                fileDocument.setExpiresAt(document.getLong(Constants.EXPIRES_AT));
            }
            if (document.get(Constants.IS_PRIVACY) != null) {
                fileDocument.setIsPrivacy(document.getBoolean(Constants.IS_PRIVACY));
            }
            if (document.get(Constants.EXTRACTION_CODE) != null) {
                fileDocument.setExtractionCode(document.getString(Constants.EXTRACTION_CODE));
            }
            if (document.get(Constants.OPERATION_PERMISSION_LIST) != null) {
                fileDocument.setOperationPermissionList(document.getList(Constants.OPERATION_PERMISSION_LIST, OperationPermission.class));
            }
        }
        mongoTemplate.save(fileDocument);
    }

    public String mkdir(String ossPath, Path prePth) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, true);
        ossService.mkdir(objectName);
        String username = getUsernameByOssPath(ossPath);
        notifyCreateFile(username, objectName, getOssRootFolderName(ossPath));
        return ossPath.substring(1) + MyWebdavServlet.PATH_DELIMITER + objectName;
    }

    public void rename(String ossPath, String pathName, String newFileName) {
        boolean isFolder = pathName.endsWith("/");
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = pathName.substring(ossPath.length());
        Path newFilePath = Paths.get(Paths.get(pathName).getParent().toString(), newFileName);
        String destinationObjectName;
        if (!isFolder) {
            destinationObjectName = getObjectName(newFilePath, ossPath, false);
        } else {
            destinationObjectName = getObjectName(newFilePath, ossPath, true);
        }
        // 复制
        if (ossService.copyObject(objectName, destinationObjectName)) {
            // 删除
            ossService.delete(objectName);
        } else {
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "重命名失败");
        }
        // 修改历史文件中的filename
        String username = getUsernameByOssPath(ossPath);
        String sourceFileId = getFileId(getOssRootFolderName(ossPath), objectName, username);
        String destinationFileId = getFileId(getOssRootFolderName(ossPath), destinationObjectName, username);
        fileVersionService.rename(sourceFileId, destinationFileId);
        // 通知文件创建成功
        notifyCreateFile(getUsernameByOssPath(ossPath), objectName, getOssRootFolderName(ossPath));
        String rootFolderName = getOssRootFolderName(ossPath);
        Path fromPath = Paths.get(rootFolderName, objectName);
        Path toPath = Paths.get(rootFolderName, newFileName);
        commonFileService.pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), getUsernameByOssPath(ossPath), "重命名");
        renameAfter(ossPath, pathName, isFolder, objectName, newFilePath);
    }

    private void renameAfter(String ossPath, String pathName, boolean isFolder, String objectName, Path newFilePath) {
        // 删除临时文件，如果有的话
        deleteTemp(ossPath, objectName);
        // 检查该目录是否有其他依赖的缓存等等。。
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").regex("^" + pathName));
        List<FileDocument> fileDocumentList = mongoTemplate.findAllAndRemove(query, FileDocument.class);
        String newPathName = newFilePath.toString();
        if (isFolder) {
            newPathName += MyWebdavServlet.PATH_DELIMITER;
        }
        // 修改关联的分享文件
        String finalNewPathName = newPathName;
        String username = getUsernameByOssPath(ossPath);
        String oldPath = pathName.substring(username.length());
        List<FileDocument> newList = new ArrayList<>();
        for (FileDocument fileDocument : fileDocumentList) {
            String oldId = fileDocument.getId();
            String newId = CharSequenceUtil.replace(oldId, pathName, finalNewPathName);
            String newPath = CharSequenceUtil.replace(fileDocument.getPath(), oldPath, finalNewPathName);
            fileDocument.setId(newId);
            fileDocument.setPath(newPath);
            if (pathName.endsWith(oldId)) {
                fileDocument.setName(newFilePath.getFileName().toString());
            }
            newList.add(fileDocument);
        }
        if (!newList.isEmpty()) {
            mongoTemplate.insertAll(newList);
        }
        // 修改关联的分享配置
        Query shareQuery = new Query();
        shareQuery.addCriteria(Criteria.where(Constants.FILE_ID).regex("^" + pathName));
        List<ShareDO> shareDOList = mongoTemplate.findAllAndRemove(shareQuery, ShareDO.class);
        List<ShareDO> newShareDOList = new ArrayList<>();
        for (ShareDO shareDO : shareDOList) {
            String oldFileId = shareDO.getFileId();
            String newFileId = CharSequenceUtil.replace(oldFileId, pathName, finalNewPathName);
            shareDO.setFileId(newFileId);
            if (pathName.endsWith(oldFileId)) {
                shareDO.setFileName(newFilePath.getFileName().toString());
            }
            newShareDOList.add(shareDO);
        }
        if (!newShareDOList.isEmpty()) {
            mongoTemplate.insertAll(newShareDOList);
        }
    }

    /**
     * 删除临时文件，如果有的话
     *
     * @param ossPath    ossPath
     * @param objectName objectName
     */
    private void deleteTemp(String ossPath, String objectName) {
        Path tempFilePath = Paths.get(fileProperties.getRootDir(), ossPath, objectName);
        if (Files.exists(tempFilePath)) {
            PathUtil.del(tempFilePath);
        }
    }

    public void delete(String ossPath, List<String> pathNameList) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        for (String pathName : pathNameList) {
            String objectName = pathName.substring(ossPath.length());
            // 删除对象
            if (ossService.delete(objectName)) {
                // 删除文件历史版本，如果有的话
                deleteHistory(ossPath, objectName);
                notifyDeleteFile(ossPath, objectName);
                // 删除临时文件，如果有的话
                deleteTemp(ossPath, objectName);
                // 删除依赖，如果有的话
                Query query = new Query();
                query.addCriteria(Criteria.where("_id").regex("^" + ReUtil.escape(pathName)));
                List<FileDocument> fileDocumentList = mongoTemplate.findAllAndRemove(query, FileDocument.class);
                Query shareQuery = new Query();
                List<String> fileIds = fileDocumentList.stream().map(FileBase::getId).toList();
                shareQuery.addCriteria(Criteria.where(Constants.FILE_ID).in(fileIds));
                mongoTemplate.remove(shareQuery, ShareDO.class);
            }
        }
    }

    private void deleteHistory(String ossPath, String objectName) {
        String username = getUsernameByOssPath(ossPath);
        String fileId = getFileId(getOssRootFolderName(ossPath), objectName, username);
        fileVersionService.deleteAll(fileId);
    }

    public ResponseEntity<Object> thumbnail(String ossPath, String pathName) {
        Optional<FileDocument> file = Optional.empty();
        FileDocument fileDocument = mongoTemplate.findById(pathName, FileDocument.class);
        if (fileDocument != null && fileDocument.getContent() != null) {
            file = Optional.of(fileDocument);
        } else {
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            String objectName = pathName.substring(ossPath.length());
            String tempFileName = SecureUtil.md5(pathName) + Paths.get(pathName).getFileName();
            File tempFile = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), tempFileName).toFile();
            try {
                FileInfo fileInfo = ossService.getThumbnail(objectName, tempFile, 256);
                String username = getUsernameByOssPath(ossPath);
                FileDocument thumbnailDoc = fileInfo.toFileDocument(ossPath, userService.getUserIdByUserName(username));
                thumbnailDoc.setContent(FileUtil.readBytes(tempFile));
                if (fileDocument != null) {
                    Query query = new Query().addCriteria(Criteria.where("_id").is(pathName));
                    Update update = new Update();
                    update.set("content", thumbnailDoc.getContent());
                    mongoTemplate.upsert(query, update, FileDocument.class);
                } else {
                    mongoTemplate.save(thumbnailDoc);
                }
                if (thumbnailDoc.getContent() != null) {
                    file = Optional.of(thumbnailDoc);
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            } finally {
                if (tempFile.exists()) {
                    FileUtil.del(tempFile);
                }
            }
        }
        return commonFileService.getObjectResponseEntity(file);
    }

    public FileIntroVO addFile(String ossPath, Boolean isFolder, Path prePth) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, isFolder);
        Path tempFileAbsolutePath = Paths.get(fileProperties.getRootDir(), prePth.toString());
        if (ossService.doesObjectExist(objectName)) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "该文件已存在");
        }
        FileInfo fileInfo;
        BucketInfo bucketInfo = CaffeineUtil.getOssDiameterPrefixCache(ossPath);
        Path path = Paths.get(ossPath);
        try {
            if (Boolean.TRUE.equals(isFolder)) {
                ossService.mkdir(objectName);
                fileInfo = BaseOssService.newFileInfo(objectName, bucketInfo.getBucketName());
            } else {
                Path tempFileAbsoluteFolderPath = tempFileAbsolutePath.getParent();
                PathUtil.mkdir(tempFileAbsoluteFolderPath);
                if (!Files.exists(tempFileAbsolutePath)) {
                    Files.createFile(tempFileAbsolutePath);
                }
                ossService.uploadFile(tempFileAbsolutePath, objectName);
                fileInfo = BaseOssService.newFileInfo(objectName, bucketInfo.getBucketName(), tempFileAbsolutePath.toFile());
                Files.delete(tempFileAbsolutePath);
            }
            notifyCreateFile(path.subpath(0, 1).toString(), objectName, bucketInfo.getFolderName());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "新建文件失败");
        }
        String userId = userService.getUserIdByUserName(path.subpath(0, 1).toString());
        return fileInfo.toFileIntroVO(ossPath, userId);
    }

    public void putObjectText(String ossPath, Path prePth, String contentText) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);

        try (AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName)) {
            // 修改文件之前保存历史版本
            String username = getUsernameByOssPath(ossPath);
            if (!username.equals(userLoginHolder.getUsername())) {
                throw new CommonException(ExceptionType.PERMISSION_DENIED);
            }
            String fileId = getFileId(getOssRootFolderName(ossPath), objectName, username);
            fileVersionService.saveFileVersion(abstractOssObject, fileId);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        InputStream inputStream = new ByteArrayInputStream(CharSequenceUtil.bytes(contentText, StandardCharsets.UTF_8));
        ossService.write(inputStream, ossPath, objectName);
        notifyUpdateFile(ossPath, objectName, contentText.length());
    }

    public void download(String ossPath, Path prePth, HttpServletRequest request, HttpServletResponse response) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        try (AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName);
             InputStream inputStream = abstractOssObject.getInputStream();
             InputStream inStream = new BufferedInputStream(inputStream, 2048);
             OutputStream outputStream = response.getOutputStream()) {
            FileInfo fileInfo = ossService.getFileInfo(objectName);
            String encodedFilename = URLEncoder.encode(fileInfo.getName(), StandardCharsets.UTF_8);
            String suffix = FileUtil.getSuffix(encodedFilename);
            // 设置响应头
            response.setContentType(FileContentTypeUtils.getContentType(suffix));
            long fileSize = abstractOssObject.getContentLength();
            String range = request.getHeader(HttpHeaders.RANGE);
            if (CharSequenceUtil.isNotBlank(range)) {
                // 处理 Range 请求
                handlerRange(response, ossService, objectName, outputStream, encodedFilename, fileSize, range);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
                IoUtil.copy(inStream, outputStream);
            }
        } catch (ClientAbortException ignored) {
            // ignored error
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 处理 Range 请求
     *
     * @param response        HttpServletResponse
     * @param ossService      IOssService
     * @param objectName      objectName
     * @param outputStream    response OutputStream
     * @param encodedFilename 编码后的文件名
     * @param fileSize        文件总大小
     * @param range           header range 的 值
     */
    private void handlerRange(HttpServletResponse response, IOssService ossService, String objectName, OutputStream outputStream, String encodedFilename, long fileSize, String range) {
        long[] ranges = parseRange(range, fileSize);
        long start = ranges[0];
        long end = ranges[1] == -1 ? fileSize - 1 : ranges[1];
        try (AbstractOssObject rangeObject = ossService.getAbstractOssObject(objectName, start, end);
             InputStream rangeIn = rangeObject.getInputStream()) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=" + encodedFilename);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            String contentRange = "bytes " + start + "-" + end + "/" + fileSize;
            response.setHeader(HttpHeaders.CONTENT_RANGE, contentRange);
            long length = end - start + 1;
            response.setContentLengthLong(length);
            IoUtil.copy(rangeIn, outputStream);
        } catch (ClientAbortException | IORuntimeException ignored) {
            // ignored error
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private long[] parseRange(String range, long contentLength) {
        String[] parts = range.substring("bytes=".length()).split("-");
        long start = parseLong(parts[0], 0L);
        long end = parseLong(parts.length > 1 ? parts[1] : "", contentLength - 1);
        if (end < start) {
            end = start;
        }
        return new long[]{start, end};
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public FileDocument getFileDocument(String ossPath, String pathName) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = pathName.substring(ossPath.length());
        try (AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName)) {
            if (abstractOssObject == null) {
                return null;
            }
            FileInfo fileInfo = abstractOssObject.getFileInfo();
            String username = getUsernameByOssPath(ossPath);
            String userId = userService.getUserIdByUserName(username);
            return fileInfo.toFileDocument(ossPath, userId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 设置ossPath所关联的FileDocument
     *
     * @param userId      userId
     * @param fileId      fileId 例如: jmal/tencent/新建文件夹/屏幕录制.2020-03-04 16_36_03.gif
     * @param objectName  objectName
     * @param ossPath     ossPath 例如: /jmal/tencent
     * @param ossRootPath ossRootPath 例如: tencent
     */
    public void setOssPath(String userId, String fileId, String objectName, String ossPath, boolean ossRootPath) {
        if (objectName.endsWith("/") || objectName.equals("")) {
            // 设置其下的所有文件
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            List<FileDocument> fileDocumentListOld = removeOssPathFile(userId, fileId, ossPath, ossRootPath, false);

            List<FileInfo> list = ossService.getAllObjectsWithPrefix(objectName);
            List<FileDocument> fileDocumentList = list.parallelStream().map(fileInfo -> fileInfo.toFileDocument(ossPath, userId)).toList();
            fileDocumentListOld.addAll(fileDocumentList);
            List<FileDocument> newFileDocumentList = fileDocumentListOld.stream().distinct().toList();
            // 插入oss目录下的共享文件
            mongoTemplate.insertAll(newFileDocumentList);
        }
    }

    /**
     * 删除ossPath所关联的FileDocument
     *
     * @param userId      userId
     * @param fileId      fileId 例如: jmal/tencent/新建文件夹/屏幕录制.2020-03-04 16_36_03.gif
     * @param ossPath     ossPath 例如: /jmal/tencent
     * @param ossRootPath ossRootPath 例如: tencent
     * @param unSetShare  移除 share 属性
     */
    public List<FileDocument> removeOssPathFile(String userId, String fileId, String ossPath, boolean ossRootPath, boolean unSetShare) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        String path;
        if (ossRootPath) {
            path = ossPath.substring(1);
        } else {
            path = fileId;
        }
        query.addCriteria(Criteria.where("_id").regex("^" + ReUtil.escape(path)));
        List<FileDocument> fileDocumentList = mongoTemplate.findAllAndRemove(query, FileDocument.class);
        List<FileDocument> list = new ArrayList<>();
        for (FileDocument fileDocument : fileDocumentList) {
            if (unSetShare) {
                // 移除 share 属性
                fileDocument.setIsShare(null);
                fileDocument.setIsPrivacy(null);
                fileDocument.setExpiresAt(null);
                fileDocument.setExtractionCode(null);
                fileDocument.setShareBase(null);
            }
            // 如果 favorite 和 share 属性都没有了就过滤掉
            if (BooleanUtil.isFalse(fileDocument.getIsFavorite()) && fileDocument.getIsShare() == null) {
                continue;
            }
            list.add(fileDocument);
        }
        return list;
    }
}
