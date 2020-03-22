package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    IUploadFileService fileService;

    @Autowired
    MongoTemplate mongoTemplate;

    @Override
    public ResponseResult<Object> generateLink(ShareBO share) {
        ShareBO shareBO = findByFileId(share.getFileId());
        if(shareBO == null){
            share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
            shareBO = mongoTemplate.save(share,COLLECTION_NAME);
        }
        return ResultUtil.success(shareBO.getId());
    }

    @Override
    public ResponseResult<Object> accessShare(String shareId, Integer pageIndex, Integer pageSize) {
        ShareBO shareBO = mongoTemplate.findById(shareId, ShareBO.class, COLLECTION_NAME);
        if(shareBO == null){
            return ResultUtil.success("该链接已失效");
        }
        if(!checkWhetherExpired(shareBO)){
            return ResultUtil.success("该链接已失效");
        }
        UploadApiParam uploadApiParam = new UploadApiParam();
        uploadApiParam.setPageIndex(pageIndex);
        uploadApiParam.setPageSize(pageSize);
        uploadApiParam.setUserId(shareBO.getUserId());
        if(shareBO.getIsFile()){
            List<FileDocument> list = new ArrayList<>();
            FileDocument fileDocument = fileService.getById(shareBO.getFileId());
            if(fileDocument != null){
                list.add(fileDocument);
            }
            return ResultUtil.success(list);
        }
        return fileService.searchFileAndOpenDir(uploadApiParam, shareBO.getFileId());
    }

    @Override
    public ShareBO getShare(String share) {
        return mongoTemplate.findById(share, ShareBO.class, COLLECTION_NAME);
    }

    @Override
    public boolean checkWhetherExpired(ShareBO shareBO) {
        if(shareBO != null){
            LocalDateTime expireDate = shareBO.getExpireDate();
            if(expireDate == null){
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
    public ResponseResult<Object> accessShareOpenDir(ShareBO shareBO, String fileId, Integer pageIndex, Integer pageSize) {
        UploadApiParam uploadApiParam = new UploadApiParam();
        uploadApiParam.setUserId(shareBO.getUserId());
        uploadApiParam.setPageIndex(pageIndex);
        uploadApiParam.setPageSize(pageSize);
        return fileService.searchFileAndOpenDir(uploadApiParam, fileId);
    }

    private ShareBO findByFileId(String fileId){
        Query query = new Query();
        query.addCriteria(Criteria.where("fileId").is(fileId));
        return mongoTemplate.findOne(query,ShareBO.class,COLLECTION_NAME);
    }
}
