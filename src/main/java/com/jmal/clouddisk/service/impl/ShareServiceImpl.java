package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.LuceneService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jmal.clouddisk.controller.rest.ShareController.SHARE_EXPIRED;
import static com.jmal.clouddisk.webdav.MyWebdavServlet.PATH_DELIMITER;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements IShareService {

    private final IFileService fileService;

    private final IShareDAO shareDAO;

    private final IFileDAO fileDAO;

    private final CommonFileService commonFileService;

    private final IWebsiteSettingDAO websiteSettingDAO;

    private final CommonUserService userService;

    private final WebOssService webOssService;

    private final UserLoginHolder userLoginHolder;

    private final LuceneService luceneService;

    @Override
    public ResponseResult<Object> generateLink(ShareDO share) {
        ShareDO shareDO = findByFileId(share.getFileId());
        share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        FileDocument file = commonFileService.getById(share.getFileId());
        if (file == null) {
            return ResultUtil.warning("文件不存在");
        }
        if (BooleanUtil.isTrue(file.getIsShare()) && !BooleanUtil.isTrue(file.getShareBase())) {
            // 设置子分享,继承上级分享
            return subShare(share.getShortId(), file);
        }
        long expireAt = Long.MAX_VALUE;
        if (share.getExpireDate() != null) {
            expireAt = TimeUntils.getMilli(share.getExpireDate());
        }
        share.setIsFolder(file.getIsFolder());
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
            shareDO = shareDAO.save(share);
        } else {
            setShortId(share, shareDO);
            updateShare(share, shareDO, file);
        }

        ShareVO shareVO = getShareVO(share, shareDO);
        shareVO.setShareBase(true);
        // 判断要分享的文件是否为oss文件
        checkOssPath(share, shareDO.getUserId(), file);

        // 修改文件夹下已经分享的文件
        List<String> subShareFileIdList = getSubShare(file);

        // 设置文件的分享属性
        fileService.setShareFile(file, expireAt, share);

        // 修改文件夹下已经分享的文件
        updateSubShare(shareDO, subShareFileIdList);
        return ResultUtil.success(shareVO);
    }

    private List<String> getSubShare(FileDocument file) {
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            // 共享文件夹及其下的所有文件
            return fileDAO.findIdSubShare(file.getUserId(), ReUtil.escape(file.getPath() + file.getName() + "/"));
        }
        return Collections.emptyList();
    }

    private void updateSubShare(ShareDO shareDO, List<String> subShareFileIdList) {
        if (subShareFileIdList == null || subShareFileIdList.isEmpty()) {
            return;
        }
        // 修改子分享配置
        shareDAO.updateSubShare(shareDO.getId(), shareDO.getIsPrivacy(), shareDO.getExtractionCode(), subShareFileIdList);
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
            shareDO = shareDAO.save(share);
        } else {
            setShortId(share, shareDO);
            updateShare(share, shareDO, file);
        }
        // 添加subShare属性
        fileDAO.setSubShareByFileId(file.getId());
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
        String shortId = Base64.encodeUrlSafe(Convert.toStr(RandomUtil.randomInt(1000, 1000000)));
        if (isExistsShareShortId(shortId)) {
            return generateShortId(share);
        }
        return shortId;
    }

    private boolean isExistsShareShortId(String shortId) {
        return shareDAO.existsByShortId(shortId);
    }

    private void checkOssPath(ShareDO share, String userId, FileDocument file) {
        Path path = Paths.get(share.getFileId());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            // oss 文件 或 目录
            if (!fileDAO.existsById(file.getId())) {
                fileDAO.save(file);
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
        shareDO.setFileName(file.getName());
        shareDO.setShortId(share.getShortId());
        shareDO.setExpireDate(share.getExpireDate());
        if (share.getOperationPermissionList() != null) {
            shareDO.setOperationPermissionList(share.getOperationPermissionList());
        }
        shareDO.setIsPrivacy(share.getIsPrivacy());
        if (Boolean.TRUE.equals(share.getIsPrivacy()) && shareDO.getExtractionCode() == null) {
            shareDO.setExtractionCode(generateExtractionCode());
        }
        if (!share.getIsPrivacy() && shareDO.getExtractionCode() != null) {
            shareDO.setExtractionCode(null);
        }
        if (shareDO.getExtractionCode() != null) {
            share.setExtractionCode(shareDO.getExtractionCode());
        }
        shareDAO.save(shareDO);
        share.setId(shareDO.getId());
        // 更新子分享
        updateSubShare(share, shareDO.getId());
    }

    private void updateSubShare(ShareDO share, String fatherShareId) {
        ShareDO entity = shareDAO.findByFatherShareId(fatherShareId);
        if (entity != null) {
            entity.setExpireDate(share.getExpireDate());
            entity.setIsPrivacy(share.getIsPrivacy());
            entity.setExtractionCode(share.getExtractionCode());
            entity.setOperationPermissionList(share.getOperationPermissionList());
            shareDAO.save(entity);
        }
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
        ShareDO shareDO = shareDAO.findById(shareId);
        if (shareDO == null) {
            return ResultUtil.warning(Constants.LINK_FAILED);
        }
        if (shareCode.equals(shareDO.getExtractionCode())) {
            // 验证成功 返回share-token
            // share-token有效期为3个小时
            String shareToken = ShortSignedIdUtil.generateToken(shareId, LocalDateTimeUtil.now().plusHours(3));
            return ResultUtil.success(shareToken);
        }
        return ResultUtil.warning("提取码有误");
    }

    @Override
    public boolean hasSubShare(List<String> shareIdList) {
        return shareDAO.existsSubShare(shareIdList);
    }

    @Override
    public boolean folderSubShare(String fileId) {
        FileBaseDTO fileBaseDTO = fileDAO.findFileBaseDTOById(fileId);
        if (fileBaseDTO == null || !BooleanUtil.isTrue(fileBaseDTO.getIsFolder())) {
            return false;
        }
        return fileDAO.existsFolderSubShare(fileBaseDTO.getUserId(), ReUtil.escape(fileBaseDTO.getPath() + fileBaseDTO.getName() + "/"));
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
        FileDocument fromFileDocument = commonFileService.getById(shareDO.getFileId());
        if (fromFileDocument == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "分享文件不存在");
        }
        if (!BooleanUtil.isTrue(fromFileDocument.getIsFolder())) {
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
            toFileDocument = commonFileService.getById(upload.getFileId());
        }

        if (toFileDocument == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "文件不存在");
        }
        if (!BooleanUtil.isTrue(toFileDocument.getIsFolder())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "只能挂载到文件夹下");
        }
        // 判断是否有挂载
        if (existsMountFile(fromFileDocument.getId(), upload.getUserId())) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "已经挂载过了");
        }
        FileDocument fileDocument = getFileDocument(upload, fromFileDocument, toFileDocument);
        // 判断是否存在同名文件夹
        String existsFileId = fileDAO.findIdByUserIdAndPathAndName(fileDocument.getUserId(), fileDocument.getPath(), fileDocument.getName());
        if (CharSequenceUtil.isNotEmpty(existsFileId)) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "已存在同名文件夹");
        }
        // 创建挂载
        String fileId = fileDAO.upsertMountFile(fileDocument);
        if (CharSequenceUtil.isNotEmpty(fileId)) {
            luceneService.pushCreateIndexQueue(fileId);
        }
    }

    private static FileDocument getFileDocument(UploadApiParamDTO upload, FileDocument fromFileDocument, FileDocument toFileDocument) {
        FileDocument fileDocument = new FileDocument();
        fileDocument.setIsFolder(true);
        fileDocument.setName(fromFileDocument.getName());
        fileDocument.setPath(toFileDocument.getPath() + toFileDocument.getName() + "/");
        fileDocument.setUserId(upload.getUserId());
        fileDocument.setIsFavorite(false);
        fileDocument.setUploadDate(fromFileDocument.getUploadDate());
        fileDocument.setUpdateDate(fromFileDocument.getUpdateDate());
        fileDocument.setMountFileId(fromFileDocument.getId());
        return fileDocument;
    }

    @Override
    public ResponseResult<Object> generateShareToken(String fileId) {
        FileDocument fileDocument = commonFileService.getById(fileId);
        if (fileDocument == null || fileDocument.getShareId() == null) {
            return ResultUtil.warning("文件不存在");
        }
        ShareDO shareDO = getShare(fileDocument.getShareId());
        if (shareDO == null || !BooleanUtil.isTrue(shareDO.getIsPrivacy())) {
            return ResultUtil.warning("分享不存在或不是私密分享");
        }
        if (!fileDocument.getUserId().equals(userLoginHolder.getUserId()) && !existsMountFile(shareDO.getFileId(), userLoginHolder.getUserId())) {
            return ResultUtil.error(ExceptionType.PERMISSION_DENIED);
        }

        String shareToken = ShortSignedIdUtil.generateToken(shareDO.getId(), LocalDateTimeUtil.now().plusHours(3));
        return ResultUtil.success(shareToken);
    }

    @Override
    public Map<String, String> getMountFileInfo(String fileId, String fileUserId) {
        // 1.获取文件信息
        FileDocument fileDocument = commonFileService.getById(fileId);
        // 2. 获取分享信息
        ShareDO shareDO = getShare(fileDocument.getShareId());
        // 3. 获取基础分享文件信息
        FileDocument shareBaseFile = commonFileService.getById(shareDO.getFileId());
        // 4.获取挂载信息
        String mountFilePath = getPathByMountFile(shareDO.getFileId(), userLoginHolder.getUserId());
        if (CharSequenceUtil.isBlank(mountFilePath)) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "挂载文件不存在");
        }
        // 5.获取文件上父级目录信息
        String parentName = Paths.get(fileDocument.getPath()).toFile().getName();
        String parentPath = Paths.get(fileDocument.getPath()).getParent().toString();
        if (parentPath.length() > 1) {
            parentPath = parentPath + PATH_DELIMITER;
        }
        String fileUsername = userService.getUserNameById(fileUserId);
        FileDocument folderInfo = fileService.getFileDocumentByPathAndName(parentPath, parentName, fileUsername);
        if (folderInfo == null) {
            return Map.of();
        }
        String path = mountFilePath + fileDocument.getPath().substring(shareBaseFile.getPath().length());
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
            FileDocument otherFileDocument = commonFileService.getById(otherFileId);
            // 2. 获取分享信息
            ShareDO shareDO = getShare(otherFileDocument.getShareId());
            // 3. 获取基础分享文件信息
            FileDocument shareBaseFile = commonFileService.getById(shareDO.getFileId());
            // 4. 获取挂载信息
            String mountFilePath = getPathByMountFile(shareDO.getFileId(), userLoginHolder.getUserId());
            if (CharSequenceUtil.isBlank(mountFilePath)) {
                return "";
            }
            String folderName = Paths.get(path).toFile().getName();
            String folderPath = shareBaseFile.getPath() + path.substring(mountFilePath.length());
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
        if (!BooleanUtil.isTrue(shareDO.getIsFolder())) {
            List<FileDocument> list = new ArrayList<>();
            FileDocument fileDocument = commonFileService.getById(shareDO.getFileId());
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
            ShortSignedIdUtil.VerificationResult result = ShortSignedIdUtil.verifyToken(shareToken);
            if (!shareDO.getId().equals(result.getKey()) || !result.isValid()) {
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

    @Override
    public boolean existsMountFile(String fileId, String userId) {
        return fileDAO.existsByUserIdAndMountFileId(userId, fileId);
    }

    private String getPathByMountFile(String fileId, String userId) {
        return fileDAO.findMountFilePath(fileId, userId);
    }

    @Override
    public ShareDO getShare(String shareId) {
        if (MongoUtil.isValidObjectId(shareId)) {
            return shareDAO.findById(shareId);
        } else {
            return shareDAO.findByShortId(shareId);
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

    public Page<ShareDO> getShareList(UploadApiParamDTO upload) {
        Page<ShareDO> shareDOPage = shareDAO.findShareList(upload);
        if (shareDOPage.isEmpty()) {
            return shareDOPage;
        }
        List<ShareDO> shareDOList = shareDOPage.getContent();
        // 获取shareDOList中的fileId
        List<String> fileIdList = shareDOList.stream().map(ShareDO::getFileId).toList();
        // 查询fileIdList是否存在
        List<String> existFileIdList = fileService.findByIdIn(fileIdList);
        // 找出shareDOList中的fileId不在fileDocumentList中的数据
        List<String> notExistFileIdList = fileIdList.stream().filter(fileId -> !existFileIdList.contains(fileId)).toList();
        if (notExistFileIdList.isEmpty()) {
            return shareDOPage;
        }
        // 删除shareDOList中的fileId不在fileDocumentList中的数据
        shareDAO.removeByFileIdIn(notExistFileIdList);
        shareDOList.removeIf(shareDO -> notExistFileIdList.contains(shareDO.getFileId()));
        return shareDOPage;
    }

    private ShareDO findByFileId(String fileId) {
        return shareDAO.findByFileId(fileId);
    }

    @Override
    public ResponseResult<Object> shareList(UploadApiParamDTO upload) {
        ResponseResult<Object> result = ResultUtil.genResult();
        Page<ShareDO> shareDOPage = getShareList(upload);
        List<ShareDO> shareDOList = shareDOPage.getContent();
        result.setCount(shareDOPage.getTotalElements());
        result.setData(shareDOList);
        return result;
    }

    @Override
    public ResponseResult<Object> cancelShare(String[] shareId) {
        List<ShareDO> shareDOList = shareDAO.findAllAndRemove(List.of(shareId));
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
            fileDAO.unsetSubShareByFileId(shareDO.getFileId());
            return;
        }

        // 删除subShare
        shareDAO.removeByFatherShareId(shareDO.getId());

        String fileId = shareDO.getFileId();
        FileDocument fileDocument = commonFileService.getById(shareDO.getFileId());
        if (fileDocument == null) {
            return;
        }
        fileService.unsetShareFile(fileDocument);
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            // 设置 share 属性
            List<FileDocument> fileDocumentList = webOssService.removeOssPathFile(shareDO.getUserId(), shareDO.getFileId(), ossPath, false, true);
            fileDAO.saveAll(fileDocumentList);
        }
        if (fileDocument.getOssFolder() != null) {
            // oss 根目录
            Path path1 = Paths.get(userService.getUserNameById(shareDO.getUserId()), fileDocument.getOssFolder());
            String ossPath1 = CaffeineUtil.getOssPath(path1);
            if (ossPath1 != null) {
                // 移除 share 属性
                List<FileDocument> fileDocumentList = webOssService.removeOssPathFile(shareDO.getUserId(), shareDO.getFileId(), ossPath1, true, true);
                fileDAO.saveAll(fileDocumentList);
            }
        }
        // 移除挂载文件
        fileDAO.removeByMountFileIdIn(Collections.singletonList(fileId));
    }

    @Override
    public void deleteAllByUser(List<ConsumerDO> userList) {
        if (userList == null || userList.isEmpty()) {
            return;
        }
        userList.forEach(user -> {
            String userId = user.getId();
            shareDAO.removeByUserId(userId);
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
        WebsiteSettingDO websiteSettingDO = websiteSettingDAO.findOne();
        if (websiteSettingDO != null) {
            sharerDTO.setNetdiskName(websiteSettingDO.getNetdiskName());
            sharerDTO.setNetdiskLogo(websiteSettingDO.getNetdiskLogo());
            sharerDTO.setIframe(websiteSettingDO.getIframe());
        }
        return ResultUtil.success(sharerDTO);
    }

}
