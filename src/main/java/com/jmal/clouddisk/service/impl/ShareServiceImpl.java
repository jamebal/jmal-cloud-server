package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base62;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.LuceneService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jmal.clouddisk.controller.rest.ShareController.SHARE_EXPIRED;
import static com.jmal.clouddisk.webdav.MyWebdavServlet.PATH_DELIMITER;

/**
 * @Description 分享
 * @Author jmal
 * @Date 2020-03-17 16:21
 */
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements IShareService {

    public static final String COLLECTION_NAME = "share";

    private final IFileService fileService;

    private final MongoTemplate mongoTemplate;

    private final UserServiceImpl userService;

    private final WebOssService webOssService;

    private final UserLoginHolder userLoginHolder;

    private final LuceneService luceneService;

    @Override
    public ResponseResult<Object> generateLink(ShareDO share) {
        ShareDO shareDO = findByFileId(share.getFileId());
        share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        FileDocument file = fileService.getById(share.getFileId());
        if (BooleanUtil.isTrue(file.getIsShare()) && !BooleanUtil.isTrue(file.getShareBase())) {
            // 设置子分享,继承上级分享
            return subShare(share.getShortId(), file);
        }
        long expireAt = Long.MAX_VALUE;
        if (share.getExpireDate() != null) {
            expireAt = TimeUntils.getMilli(share.getExpireDate());
        }
        share.setIsFolder(share.getIsFolder());
        if (expireAt < Long.MAX_VALUE) {
            share.setExpireDate(LocalDateTimeUtil.of(expireAt));
        }
        share.setFileName(file.getName());
        share.setContentType(file.getContentType());
        share.setIsPrivacy(BooleanUtil.isTrue(share.getIsPrivacy()));

        if (shareDO == null) {
            if (Boolean.TRUE.equals(share.getIsPrivacy())) {
                share.setExtractionCode(generateExtractionCode());
            }
            share.setShortId(generateShortId(share));
            shareDO = mongoTemplate.save(share, COLLECTION_NAME);
        } else {
            setShortId(share, shareDO);
            updateShare(share, shareDO, file);
        }
        file.setShareBase(shareDO.getShareBase());

        ShareVO shareVO = getShareVO(share, shareDO);
        shareVO.setShareBase(true);
        // 判断要分享的文件是否为oss文件
        checkOssPath(share, shareDO.getUserId(), file);
        // 设置文件的分享属性
        fileService.setShareFile(file, expireAt, share);
        return ResultUtil.success(shareVO);
    }

    private ResponseResult<Object> subShare(String shortId, FileDocument file) {
        ShareDO share = new ShareDO();
        ShareDO fatherShare = getShare(file.getShareId());
        if (fatherShare == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "遇到一点问题, 请稍后再试");
        }
        BeanUtils.copyProperties(fatherShare, share);
        share.setId(null);
        share.setShortId(shortId);
        share.setFileId(file.getId());

        ShareDO shareDO = findByFileId(file.getId());

        // 创建子分享
        share.setFatherShareId(file.getShareId());
        if (shareDO == null) {
            share.setShortId(generateShortId(share));
            share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
            share.setIsFolder(share.getIsFolder());
            share.setFileName(file.getName());
            share.setContentType(file.getContentType());
            shareDO = mongoTemplate.save(share, COLLECTION_NAME);
        } else {
            setShortId(share, shareDO);
            updateShare(share, shareDO, file);
        }
        // 添加subShare属性
        setSubShare(file.getId());
        ShareVO shareVO = getShareVO(share, shareDO);
        shareVO.setSubShare(true);
        return ResultUtil.success(shareVO);
    }

    private static ShareVO getShareVO(ShareDO share, ShareDO shareDO) {
        ShareVO shareVO = new ShareVO();
        if (share.getOperationPermissionList() == null) {
            shareVO.setOperationPermissionList(shareDO.getOperationPermissionList());
        } else {
            shareVO.setOperationPermissionList(share.getOperationPermissionList());
        }
        shareVO.setShortId(shareDO.getShortId());
        shareVO.setShareId(shareDO.getId());
        if (Boolean.TRUE.equals(share.getIsPrivacy())) {
            shareVO.setExtractionCode(share.getExtractionCode());
        }
        return shareVO;
    }

    private void setShortId(ShareDO share, ShareDO shareDO) {
        if (shareDO.getShortId() == null) {
            String shortId = generateShortId(share);
            share.setShortId(shortId);
            shareDO.setShortId(shortId);
        } else {
            share.setShortId(shareDO.getShortId());
        }
    }

    /**
     * 生成5-8位短链接字符串
     *
     * @return 链接字符串
     */
    private String generateShortId(ShareDO share) {
        if (CharSequenceUtil.isNotBlank(share.getShortId())) {
            // 检测是否已经存在
            if (isExistsShareShortId(share.getShortId())) {
                throw new CommonException(ExceptionType.WARNING.getCode(), "地址 \"" + share.getShortId() + "\" 已存在");
            }
            return share.getShortId();
        }
        String shortId = Base62.encode(Convert.toStr(RandomUtil.randomInt(1000, 1000000)));
        if (isExistsShareShortId(shortId)) {
            return generateShortId(share);
        }
        return shortId;
    }

    private boolean isExistsShareShortId(String shortId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.SHORT_ID).is(shortId));
        return mongoTemplate.exists(query, ShareDO.class, COLLECTION_NAME);
    }

    private void checkOssPath(ShareDO share, String userId, FileDocument file) {
        Path path = Paths.get(share.getFileId());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            // oss 文件 或 目录
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(file.getId()));
            if (!mongoTemplate.exists(query, FileDocument.class)) {
                mongoTemplate.save(file);
            }
            String objectName = share.getFileId().substring(ossPath.length());
            webOssService.setOssPath(share.getUserId(), share.getFileId(), objectName, ossPath, false);
        }
        if (file.getOssFolder() != null) {
            // oss 根目录
            Path path1 = Paths.get(userService.getUserNameById(userId), file.getOssFolder());
            String ossPath1 = CaffeineUtil.getOssPath(path1);
            if (ossPath1 != null) {
                String objectName = WebOssService.getObjectName(path1, ossPath1, true);
                webOssService.setOssPath(share.getUserId(), null, objectName, ossPath1, true);
            }
        }
    }

    private void updateShare(ShareDO share, ShareDO shareDO, FileDocument file) {
        Query query = new Query().addCriteria(Criteria.where(Constants.FILE_ID).is(share.getFileId()));
        Update update = new Update();
        update.set("fileName", file.getName());
        update.set(Constants.SHORT_ID, share.getShortId());
        if (share.getExpireDate() != null) {
            update.set(Constants.EXPIRE_DATE, share.getExpireDate());
        } else {
            update.unset(Constants.EXPIRE_DATE);
        }
        if (share.getOperationPermissionList() != null) {
            update.set(Constants.OPERATION_PERMISSION_LIST, share.getOperationPermissionList());
        }
        update.set(Constants.IS_PRIVACY, share.getIsPrivacy());
        if (Boolean.TRUE.equals(share.getIsPrivacy()) && shareDO.getExtractionCode() == null) {
            shareDO.setExtractionCode(generateExtractionCode());
            update.set(Constants.EXTRACTION_CODE, shareDO.getExtractionCode());
        }
        if (Boolean.TRUE.equals(!share.getIsPrivacy()) && shareDO.getExtractionCode() != null) {
            update.unset(Constants.EXTRACTION_CODE);
        }
        if (shareDO.getExtractionCode() != null) {
            share.setExtractionCode(shareDO.getExtractionCode());
        }
        share.setId(shareDO.getId());
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
        // 更新子分享
        updateSubShare(share, shareDO);
    }

    private void updateSubShare(ShareDO share, ShareDO shareDO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("fatherShareId").is(shareDO.getId()));
        Update update = new Update();
        update.set(Constants.EXPIRE_DATE, share.getExpireDate());
        update.set(Constants.IS_PRIVACY, share.getIsPrivacy());
        update.set(Constants.EXTRACTION_CODE, share.getExtractionCode());
        update.set(Constants.OPERATION_PERMISSION_LIST, share.getOperationPermissionList());
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
    }

    /***
     * 生成提取码
     * @return 提取码
     */
    private String generateExtractionCode() {
        return RandomUtil.randomString(4);
    }

    @Override
    public ResponseResult<Object> validShareCode(String shareId, String shareCode) {
        ShareDO shareDO = mongoTemplate.findById(shareId, ShareDO.class, COLLECTION_NAME);
        if (shareDO == null) {
            return ResultUtil.warning(Constants.LINK_FAILED);
        }
        if (shareCode.equals(shareDO.getExtractionCode())) {
            // 验证成功 返回share-token
            // share-token有效期为6个小时
            String shareToken = TokenUtil.createToken(shareId, LocalDateTimeUtil.now().plusHours(6));
            return ResultUtil.success(shareToken);
        }
        return ResultUtil.warning("提取码有误");
    }

    public void validShare(String shareToken, String shareId) {
        ShareDO shareDO = getShare(shareId);
        if (shareDO == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), Constants.LINK_FAILED);
        }
        validShare(shareToken, shareDO);
    }

    @Override
    public void mountFile(UploadApiParamDTO upload) {
        if (upload.getShareId() == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少参数 shareId");
        }
        if (upload.getUserId() == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少参数 userId");
        }
        if (upload.getFileId() == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少参数 fileId");
        }
        // 从shareId挂载到fileId
        ShareDO shareDO = getShare(upload.getShareId());
        if (shareDO == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), Constants.LINK_FAILED);
        }
        FileDocument fromFileDocument = fileService.getById(shareDO.getFileId());
        if (fromFileDocument == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "分享文件不存在");
        }
        if (BooleanUtil.isFalse(fromFileDocument.getIsFolder())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "该分享不是文件夹");
        }
        FileDocument toFileDocument;
        if (Constants.REGION_DEFAULT.equals(upload.getFileId())) {
            //  挂载到根目录
            toFileDocument = new FileDocument();
            toFileDocument.setPath("");
            toFileDocument.setName("");
            toFileDocument.setIsFolder(true);
        } else {
            toFileDocument = fileService.getById(upload.getFileId());
        }

        if (toFileDocument == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "文件不存在");
        }
        if (BooleanUtil.isFalse(toFileDocument.getIsFolder())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "只能挂载到文件夹下");
        }
        // 判断是否有挂载
        if (existsMountFile(fromFileDocument.getId(), upload.getUserId())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "已经挂载过了");
        }
        // 创建文件夹
        FileDocument fileDocument = new FileDocument();
        fileDocument.setIsFolder(true);
        fileDocument.setName(fromFileDocument.getName());
        fileDocument.setPath(toFileDocument.getPath() + toFileDocument.getName() + File.separator);
        fileDocument.setUserId(upload.getUserId());
        fileDocument.setIsFavorite(false);
        fileDocument.setUploadDate(fromFileDocument.getUploadDate());
        fileDocument.setUpdateDate(fromFileDocument.getUpdateDate());
        fileDocument.setMountFileId(fromFileDocument.getId());
        Update update = MongoUtil.getUpdate(fileDocument);
        update.set("remark", "挂载 mount");
        Query query = CommonFileService.getQuery(fileDocument);
        UpdateResult updateResult = mongoTemplate.upsert(query, update, FileDocument.class);
        if (null != updateResult.getUpsertedId()) {
            luceneService.pushCreateIndexQueue(updateResult.getUpsertedId().asObjectId().getValue().toHexString());
        }
    }

    @Override
    public ResponseResult<Object> generateShareToken(String fileId) {
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null || fileDocument.getShareId() == null) {
            return ResultUtil.warning("文件不存在");
        }
        ShareDO shareDO = getShare(fileDocument.getShareId());
        if (shareDO == null || BooleanUtil.isFalse(shareDO.getIsPrivacy())) {
            return ResultUtil.warning("分享不存在或不是私密分享");
        }
        if (!fileDocument.getUserId().equals(userLoginHolder.getUserId()) && !existsMountFile(shareDO.getFileId(), userLoginHolder.getUserId())) {
            return ResultUtil.error(ExceptionType.PERMISSION_DENIED);
        }

        // 生成share-token, share-token有效期等于分享有效期
        String shareToken = TokenUtil.createToken(shareDO.getId(), shareDO.getExpireDate());
        return ResultUtil.success(shareToken);
    }

    @Override
    public Map<String, String> getMountFileInfo(String fileId, String fileUsername) {
        // 1.获取文件信息
        FileDocument fileDocument = fileService.getById(fileId);
        // 2. 获取分享信息
        ShareDO shareDO = getShare(fileDocument.getShareId());
        // 3. 获取基础分享文件信息
        FileDocument shareBaseFile = fileService.getById(shareDO.getFileId());
        // 4.获取挂载信息
        FileDocument mountFile = getMountFile(shareDO.getFileId(), userLoginHolder.getUserId());
        if (mountFile == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "挂载文件不存在");
        }
        // 5.获取文件上父级目录信息
        String parentName = Paths.get(fileDocument.getPath()).toFile().getName();
        String parentPath = Paths.get(fileDocument.getPath()).getParent().toString();
        if (parentPath.length() > 1) {
            parentPath = parentPath + PATH_DELIMITER;
        }
        FileDocument folderInfo = fileService.getFileDocumentByPathAndName(parentPath, parentName, fileUsername);
        if (folderInfo == null) {
            return Map.of();
        }
        String path = mountFile.getPath() + fileDocument.getPath().substring(shareBaseFile.getPath().length());
        String folder = folderInfo.getId();
        return Map.of(
                "path", path,
                "folder", folder
        );
    }

    @Override
    public String getMountFolderId(String path, String fileUsername, String otherFileId) {
        try {
            // 1. 获取其他文件信息
            FileDocument otherFileDocument = fileService.getById(otherFileId);
            // 2. 获取分享信息
            ShareDO shareDO = getShare(otherFileDocument.getShareId());
            // 3. 获取基础分享文件信息
            FileDocument shareBaseFile = fileService.getById(shareDO.getFileId());
            // 4. 获取挂载信息
            FileDocument mountFile = getMountFile(shareDO.getFileId(), userLoginHolder.getUserId());
            if (mountFile == null) {
                return "";
            }
            String folderName = Paths.get(path).toFile().getName();
            String folderPath = shareBaseFile.getPath() + path.substring(mountFile.getPath().length());
            folderPath = folderPath.substring(0, folderPath.length() - folderName.length());
            FileDocument folderInfo = fileService.getFileDocumentByPathAndName(folderPath, folderName, fileUsername);
            if (folderInfo == null) {
                return "";
            }
            return folderInfo.getId();
        } catch (Exception e) {
            return "";
        }
    }

    public void validShare(String shareToken, ShareDO shareDO) {
        if (checkWhetherExpired(shareDO)) {
            throw new CommonException(ExceptionType.WARNING.getCode(), SHARE_EXPIRED);
        }
        validShareCode(shareToken, shareDO);
    }

    @Override
    public ResponseResult<Object> accessShare(ShareDO shareDO, Integer pageIndex, Integer pageSize, Boolean showFolderSize) {
        UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
        uploadApiParamDTO.setPageIndex(pageIndex);
        uploadApiParamDTO.setPageSize(pageSize);
        uploadApiParamDTO.setUserId(shareDO.getUserId());
        if (Boolean.FALSE.equals(shareDO.getIsFolder())) {
            List<FileDocument> list = new ArrayList<>();
            FileDocument fileDocument = fileService.getById(shareDO.getFileId());
            if (fileDocument != null) {
                list.add(fileDocument);
            }
            return ResultUtil.success(list);
        }
        String username = userService.getUserNameById(shareDO.getUserId());
        uploadApiParamDTO.setUsername(username);
        uploadApiParamDTO.setCurrentDirectory("/");
        uploadApiParamDTO.setShowFolderSize(showFolderSize);
        return fileService.searchFileAndOpenDir(uploadApiParamDTO, shareDO.getFileId(), null);
    }

    @Override
    public void validShareCode(String shareToken, ShareDO shareDO) {
        if (checkWhetherExpired(shareDO)) {
            throw new CommonException(ExceptionType.WARNING.getCode(), Constants.LINK_FAILED);
        }
        // 检查是否为私密链接
        if (BooleanUtil.isTrue(shareDO.getIsPrivacy())) {
            ShareVO shareVO = new ShareVO();
            BeanUtils.copyProperties(shareDO, shareVO);
            shareVO.setExtractionCode(null);
            // 先检查有没有share-token
            if (CharSequenceUtil.isBlank(shareToken)) {
                String userId = userLoginHolder.getUserId();
                if (CharSequenceUtil.isBlank(userId)) {
                    shareValidFailed(shareDO);
                }
                if (existsMountFile(shareDO.getFileId(), userId)) {
                    return;
                }
            }
            // 再检查share-token是否正确
            if (!shareDO.getId().equals(TokenUtil.getTokenKey(shareToken))) {
                // 验证失败
                shareValidFailed(shareDO);
            }
        }
    }

    /**
     * 分享验证失败·
     *
     * @param shareDO 分享信息
     */
    private static void shareValidFailed(ShareDO shareDO) {
        shareDO.setExtractionCode(null);
        shareDO.setOperationPermissionList(null);
        shareDO.setUserId(null);
        shareDO.setFileId(null);
        throw new CommonException(ExceptionType.SYSTEM_SUCCESS, shareDO);
    }

    private boolean existsMountFile(String fileId, String userId) {
        Query query = getMountQuery(fileId, userId);
        return mongoTemplate.exists(query, FileDocument.class);
    }

    private FileDocument getMountFile(String fileId, String userId) {
        Query query = getMountQuery(fileId, userId);
        return mongoTemplate.findOne(query, FileDocument.class);
    }

    private static Query getMountQuery(String fileId, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("mountFileId").is(fileId));
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        return query;
    }

    @Override
    public ShareDO getShare(String shareId) {
        if (MongoUtil.isValidObjectId(shareId)) {
            return mongoTemplate.findById(shareId, ShareDO.class, COLLECTION_NAME);
        } else {
            Query query = new Query();
            query.addCriteria(Criteria.where(Constants.SHORT_ID).is(shareId));
            return mongoTemplate.findOne(query, ShareDO.class, COLLECTION_NAME);
        }
    }

    @Override
    public ShareDO getShareByFileId(String fileId) {
        return findByFileId(fileId);
    }

    /**
     * 检查是否过期
     *
     * @param shareDO 分享信息
     * @return 是否过期 true:过期 false:未过期
     */
    private boolean checkWhetherExpired(ShareDO shareDO) {
        if (shareDO != null) {
            LocalDateTime expireDate = shareDO.getExpireDate();
            if (expireDate == null) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
            return !expireDate.isAfter(now);
        }
        return true;
    }

    @Override
    public ResponseResult<Object> accessShareOpenDir(ShareDO shareDO, String fileId, Integer pageIndex, Integer pageSize, Boolean showFolderSize) {
        UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
        uploadApiParamDTO.setUserId(shareDO.getUserId());
        uploadApiParamDTO.setPageIndex(pageIndex);
        uploadApiParamDTO.setPageSize(pageSize);

        // 判断是否为ossPath
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            uploadApiParamDTO.setUsername(path.subpath(0, 1).toString());
            uploadApiParamDTO.setCurrentDirectory(path.subpath(1, path.getNameCount()).toString());
        } else {
            String username = userService.getUserNameById(shareDO.getUserId());
            uploadApiParamDTO.setUsername(username);
        }
        uploadApiParamDTO.setShowFolderSize(showFolderSize);
        return fileService.searchFileAndOpenDir(uploadApiParamDTO, fileId, null);
    }

    @Override
    public List<ShareDO> getShareList(UploadApiParamDTO upload) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(upload.getUserId()));
        String order = FileServiceImpl.listByPage(upload, query);
        if (CharSequenceUtil.isBlank(order)) {
            query.with(Sort.by(Sort.Direction.DESC, "createDate"));
        } else {
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if ("descending".equals(order)) {
                direction = Sort.Direction.DESC;
            }
            query.with(Sort.by(direction, sortableProp));
        }
        List<ShareDO> shareDOList = mongoTemplate.find(query, ShareDO.class, COLLECTION_NAME);
        // 获取shareDOList中的fileId
        List<String> fileIdList = shareDOList.stream().map(ShareDO::getFileId).toList();
        // 查询fileIdList是否存在
        List<FileDocument> fileDocumentList = fileService.listByIds(fileIdList);
        // 找出shareDOList中的fileId不在fileDocumentList中的数据
        List<String> notExistFileIdList = fileIdList.stream().filter(fileId -> !fileDocumentList.stream().map(FileDocument::getId).toList().contains(fileId)).toList();
        if (notExistFileIdList.isEmpty()) {
            return shareDOList;
        }
        // 删除shareDOList中的fileId不在fileDocumentList中的数据
        mongoTemplate.remove(Query.query(Criteria.where(Constants.FILE_ID).in(notExistFileIdList)), COLLECTION_NAME);
        shareDOList.removeIf(shareDO -> notExistFileIdList.contains(shareDO.getFileId()));
        return shareDOList;
    }

    private ShareDO findByFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).is(fileId));
        return mongoTemplate.findOne(query, ShareDO.class, COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> shareList(UploadApiParamDTO upload) {
        ResponseResult<Object> result = ResultUtil.genResult();
        List<ShareDO> shareDOList = getShareList(upload);
        result.setCount(getShareCount(upload.getUserId()));
        result.setData(shareDOList);
        return result;
    }

    private long getShareCount(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> cancelShare(String[] shareId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in((Object[]) shareId));
        List<ShareDO> shareDOList = mongoTemplate.findAllAndRemove(query, ShareDO.class, COLLECTION_NAME);
        if (!shareDOList.isEmpty()) {
            shareDOList.forEach(this::removeShareProperty);
        }
        return ResultUtil.success();
    }

    /**
     * 移除share属性
     *
     * @param shareDO ShareDO
     */
    private void removeShareProperty(ShareDO shareDO) {
        if (shareDO.getFatherShareId() != null) {
            // 移除SUB_SHARE
            unsetSubShare(shareDO.getFileId());
            return;
        }

        // 删除subShare
        removeSubSare(shareDO);

        String fileId = shareDO.getFileId();
        FileDocument fileDocument = fileService.getById(shareDO.getFileId());
        if (fileDocument == null) {
            return;
        }
        fileService.unsetShareFile(fileDocument);
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            // 设置 share 属性
            List<FileDocument> fileDocumentList = webOssService.removeOssPathFile(shareDO.getUserId(), shareDO.getFileId(), ossPath, false, true);
            mongoTemplate.insertAll(fileDocumentList);
        }
        if (fileDocument.getOssFolder() != null) {
            // oss 根目录
            Path path1 = Paths.get(userService.getUserNameById(shareDO.getUserId()), fileDocument.getOssFolder());
            String ossPath1 = CaffeineUtil.getOssPath(path1);
            if (ossPath1 != null) {
                // 移除 share 属性
                List<FileDocument> fileDocumentList = webOssService.removeOssPathFile(shareDO.getUserId(), shareDO.getFileId(), ossPath1, true, true);
                mongoTemplate.insertAll(fileDocumentList);
            }
        }
        // 移除挂载文件
        Query query = new Query();
        query.addCriteria(Criteria.where("mountFileId").is(fileId));
        mongoTemplate.remove(query, FileDocument.class);
    }

    private void removeSubSare(ShareDO shareDO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("fatherShareId").is(shareDO.getId()));
        mongoTemplate.remove(query, ShareDO.class, COLLECTION_NAME);
    }

    /**
     * 添加subShare属性
     * @param fileId fileId
     */
    private void setSubShare(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set(Constants.SUB_SHARE, true);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    /**
     * 移除subShare属性
     * @param fileId fileId
     */
    private void unsetSubShare(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.unset(Constants.SUB_SHARE);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public void deleteAllByUser(List<ConsumerDO> userList) {
        if (userList == null || userList.isEmpty()) {
            return;
        }
        userList.forEach(user -> {
            String userId = user.getId();
            Query query = new Query();
            query.addCriteria(Criteria.where("userId").in(userId));
            mongoTemplate.remove(query, COLLECTION_NAME);
        });
    }

    @Override
    public ResponseResult<SharerDTO> getSharer(String shareId) {
        ShareDO shareDO = getShare(shareId);
        if (shareDO == null) {
            return ResultUtil.success();
        }
        String userId = shareDO.getUserId();
        SharerDTO sharerDTO = new SharerDTO();
        sharerDTO.setShareId(shareDO.getId());
        ConsumerDO consumerDO = userService.getUserInfoById(userId);
        sharerDTO.setShowName(consumerDO.getShowName());
        sharerDTO.setUsername(consumerDO.getUsername());
        sharerDTO.setUserId(userId);
        if (consumerDO.getAvatar() != null) {
            sharerDTO.setAvatar(consumerDO.getAvatar());
        }
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class, SettingService.COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDO != null) {
            sharerDTO.setNetdiskName(websiteSettingDO.getNetdiskName());
            sharerDTO.setNetdiskLogo(websiteSettingDO.getNetdiskLogo());
            sharerDTO.setIframe(websiteSettingDO.getIframe());
        }
        return ResultUtil.success(sharerDTO);
    }

}
