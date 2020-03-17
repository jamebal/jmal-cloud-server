package com.jmal.clouddisk.service.impl;

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
    public ResponseResult<Object> accessShare(String shareId) {
        ShareBO shareBO = mongoTemplate.findById(shareId, ShareBO.class);
        if(shareBO == null){
            return ResultUtil.error(-2,"该链接已失效");
        }
        UploadApiParam uploadApiParam = new UploadApiParam();
        uploadApiParam.setUserId(shareBO.getUserId());
        return fileService.searchFileAndOpenDir(uploadApiParam, shareBO.getFileId());
    }

    private ShareBO findByFileId(String fileId){
        Query query = new Query();
        query.addCriteria(Criteria.where("fileId").is(fileId));
        return mongoTemplate.findOne(query,ShareBO.class,COLLECTION_NAME);
    }
}
