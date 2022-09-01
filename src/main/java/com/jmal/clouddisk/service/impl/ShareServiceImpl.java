package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description 分享
 * @Author jmal
 * @Date 2020-03-17 16:21
 */
@Service
public class ShareServiceImpl implements IShareService {

    private static final String COLLECTION_NAME = "share";

    @Autowired
    IFileService fileService;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    IUserService userService;

    @Autowired
    SettingService settingService;

    @Override
    public ResponseResult<Object> generateLink(ShareDO share) {
        ShareDO shareDO = findByFileId(share.getFileId());
        share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        FileDocument file = fileService.getById(share.getFileId());
        long expireAt = Long.MAX_VALUE;
        if (share.getExpireDate() != null){
            expireAt = TimeUntils.getMilli(share.getExpireDate());
        }
        fileService.setShareFile(file, expireAt);
        if (shareDO == null) {
            share.setFileName(file.getName());
            share.setContentType(file.getContentType());
            shareDO = mongoTemplate.save(share, COLLECTION_NAME);
        }
        return ResultUtil.success(shareDO.getId());
    }

    @Override
    public ResponseResult<Object> accessShare(String shareId, Integer pageIndex, Integer pageSize) {
        ShareDO shareDO = mongoTemplate.findById(shareId, ShareDO.class, COLLECTION_NAME);
        if (shareDO == null) {
            return ResultUtil.success("该链接已失效");
        }
        if (!checkWhetherExpired(shareDO)) {
            return ResultUtil.success("该链接已失效");
        }
        UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
        uploadApiParamDTO.setPageIndex(pageIndex);
        uploadApiParamDTO.setPageSize(pageSize);
        uploadApiParamDTO.setUserId(shareDO.getUserId());
        if (!shareDO.getIsFolder()) {
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
    public ShareDO getShare(String share) {
        return mongoTemplate.findById(share, ShareDO.class, COLLECTION_NAME);
    }

    @Override
    public boolean checkWhetherExpired(ShareDO shareDO) {
        if (shareDO != null) {
            LocalDateTime expireDate = shareDO.getExpireDate();
            if (expireDate == null) {
                return true;
            }
            LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
            return expireDate.compareTo(now) > 0;
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
        query.addCriteria(Criteria.where("userId").is(upload.getUserId()));
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
    public ResponseResult<SharerDTO> getSharer(String userId) {
        SharerDTO sharerDTO = new SharerDTO();
        ConsumerDO consumerDO = userService.getUserInfoById(userId);
        sharerDTO.setShowName(consumerDO.getShowName());
        sharerDTO.setUsername(consumerDO.getUsername());
        sharerDTO.setUserId(userId);
        if (consumerDO.getAvatar() != null) {
            sharerDTO.setAvatar(consumerDO.getAvatar());
        }
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class, SettingService.COLLECTION_NAME_WEBSITE_SETTING);
        if(websiteSettingDO != null){
            sharerDTO.setNetdiskName(websiteSettingDO.getNetdiskName());
            sharerDTO.setNetdiskLogo(websiteSettingDO.getNetdiskLogo());
        }
        return ResultUtil.success(sharerDTO);
    }
}
