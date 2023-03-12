package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import com.jmal.clouddisk.util.TokenUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.jmal.clouddisk.controller.rest.ShareController.SHARE_EXPIRED;

/**
 * @Description 分享
 * @Author jmal
 * @Date 2020-03-17 16:21
 */
@Service
public class ShareServiceImpl implements IShareService {

    private static final String COLLECTION_NAME = "share";

    private final IFileService fileService;

    private final MongoTemplate mongoTemplate;

    private final UserServiceImpl userService;


    public ShareServiceImpl(IFileService fileService, MongoTemplate mongoTemplate, UserServiceImpl userService) {
        this.fileService = fileService;
        this.mongoTemplate = mongoTemplate;
        this.userService = userService;
    }

    @Override
    public ResponseResult<Object> generateLink(ShareDO share) {
        ShareDO shareDO = findByFileId(share.getFileId());
        share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        FileDocument file = fileService.getById(share.getFileId());
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
            shareDO = mongoTemplate.save(share, COLLECTION_NAME);
        } else {
            updateShare(share, shareDO, file);
        }
        file.setShareBase(shareDO.getShareBase());
        fileService.setShareFile(file, expireAt, share);
        ShareVO shareVO = new ShareVO();
        shareVO.setShareId(shareDO.getId());
        if (Boolean.TRUE.equals(share.getIsPrivacy())) {
            shareVO.setExtractionCode(share.getExtractionCode());
        }
        return ResultUtil.success(shareVO);
    }

    private void updateShare(ShareDO share, ShareDO shareDO, FileDocument file) {
        Query query = new Query().addCriteria(Criteria.where("fileId").is(share.getFileId()));
        Update update = new Update();
        update.set("fileName", file.getName());
        if (share.getExpireDate() != null) {
            update.set("expireDate", share.getExpireDate());
        } else {
            update.unset("expireDate");
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

    public ResponseResult<Object> validShare(String shareToken, String shareId) {
        ShareDO shareDO = getShare(shareId);
        return validShare(shareToken, shareDO);
    }

    public ResponseResult<Object> validShare(String shareToken, ShareDO shareDO) {
        if (!checkWhetherExpired(shareDO)) {
            return ResultUtil.warning(SHARE_EXPIRED);
        }
        return validShareCode(shareToken, shareDO);
    }

    @Override
    public ResponseResult<Object> accessShare(ShareDO shareDO, Integer pageIndex, Integer pageSize) {
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
        return fileService.searchFileAndOpenDir(uploadApiParamDTO, shareDO.getFileId());
    }

    @Override
    public ResponseResult<Object> validShareCode(String shareToken, ShareDO shareDO) {
        if (!checkWhetherExpired(shareDO)) {
            return ResultUtil.success(Constants.LINK_FAILED);
        }
        // 检查是否为私密链接
        if (BooleanUtil.isTrue(shareDO.getIsPrivacy())) {
            ShareVO shareVO = new ShareVO();
            BeanUtils.copyProperties(shareDO, shareVO);
            shareVO.setExtractionCode(null);
            // 先检查有没有share-token
            if (CharSequenceUtil.isBlank(shareToken)) {
                return ResultUtil.success(shareVO);
            }
            // 再检查share-token是否正确
            if (!shareDO.getId().equals(TokenUtil.getTokenKey(shareToken))) {
                // 验证失败
                return ResultUtil.success(shareVO);
            }
        }
        return null;
    }

    @Override
    public ShareDO getShare(String shareId) {
        return mongoTemplate.findById(shareId, ShareDO.class, COLLECTION_NAME);
    }

    @Override
    public boolean checkWhetherExpired(ShareDO shareDO) {
        if (shareDO != null) {
            LocalDateTime expireDate = shareDO.getExpireDate();
            if (expireDate == null) {
                return true;
            }
            LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
            return expireDate.isAfter(now);
        }
        return false;
    }

    @Override
    public boolean checkWhetherExpired(String share) {
        return checkWhetherExpired(getShare(share));
    }

    @Override
    public ResponseResult<Object> accessShareOpenDir(ShareDO shareDO, String fileId, Integer pageIndex, Integer pageSize) {
        UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
        uploadApiParamDTO.setUserId(shareDO.getUserId());
        uploadApiParamDTO.setPageIndex(pageIndex);
        uploadApiParamDTO.setPageSize(pageSize);
        return fileService.searchFileAndOpenDir(uploadApiParamDTO, fileId);
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
        return mongoTemplate.find(query, ShareDO.class, COLLECTION_NAME);
    }

    private ShareDO findByFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("fileId").is(fileId));
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
            shareDOList.forEach(shareDO -> fileService.unsetShareFile(fileService.getById(shareDO.getFileId())));
        }
        return ResultUtil.success();
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
        }
        return ResultUtil.success(sharerDTO);
    }

}
