package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Override
    public ResponseResult<Object> generateLink(ShareBO share) {
        ShareBO shareBO = findByFileId(share.getFileId());
        if(shareBO == null){
            share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
            FileDocument file = fileService.getById(share.getFileId());
            share.setFileName(file.getName());
            share.setContentType(file.getContentType());
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
        if(!shareBO.getIsFolder()){
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

    @Override
    public List<ShareBO> getShareList(UploadApiParam upload) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(upload.getUserId()));
        Integer pageSize = upload.getPageSize(), pageIndex = upload.getPageIndex();
        if (pageSize != null && pageIndex != null) {
            long skip = (pageIndex - 1) * pageSize;
            query.skip(skip);
            query.limit(pageSize);
        }
        String order = upload.getOrder();
        if(!StringUtils.isEmpty(order)){
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if("descending".equals(order)){
                direction = Sort.Direction.DESC;
            }
            query.with(new Sort(direction, sortableProp));
        }else{
            query.with(new Sort(Sort.Direction.DESC, "createDate"));
        }
        return mongoTemplate.find(query, ShareBO.class, COLLECTION_NAME);
    }

    private ShareBO findByFileId(String fileId){
        Query query = new Query();
        query.addCriteria(Criteria.where("fileId").is(fileId));
        return mongoTemplate.findOne(query,ShareBO.class,COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> sharelist(UploadApiParam upload) {
        ResponseResult<Object> result = ResultUtil.genResult();
        List<ShareBO> shareBOList = getShareList(upload);
        result.setCount(getShareCount(upload.getUserId()));
        result.setData(shareBOList);
        return result;
    }

    private long getShareCount(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        return mongoTemplate.count(query,COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> cancelShare(String[] shareId, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("_id").in(shareId));
        mongoTemplate.remove(query,COLLECTION_NAME);
        return ResultUtil.success();
    }
}
