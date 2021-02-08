package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
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

import java.nio.file.Paths;
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
    public ResponseResult<Object> generateLink(ShareDO share) {
        ShareDO shareDO = findByFileId(share.getFileId());
        if(shareDO == null){
            share.setCreateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
            FileDocument file = fileService.getById(share.getFileId());
            share.setFileName(file.getName());
            share.setContentType(file.getContentType());
            shareDO = mongoTemplate.save(share,COLLECTION_NAME);
        }
        return ResultUtil.success(shareDO.getId());
    }

    @Override
    public ResponseResult<Object> accessShare(String shareId, Integer pageIndex, Integer pageSize) {
        ShareDO shareDO = mongoTemplate.findById(shareId, ShareDO.class, COLLECTION_NAME);
        if(shareDO == null){
            return ResultUtil.success("该链接已失效");
        }
        if(!checkWhetherExpired(shareDO)){
            return ResultUtil.success("该链接已失效");
        }
        UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
        uploadApiParamDTO.setPageIndex(pageIndex);
        uploadApiParamDTO.setPageSize(pageSize);
        uploadApiParamDTO.setUserId(shareDO.getUserId());
        if(!shareDO.getIsFolder()){
            List<FileDocument> list = new ArrayList<>();
            FileDocument fileDocument = fileService.getById(shareDO.getFileId());
            if(fileDocument != null){
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
        if(shareDO != null){
            LocalDateTime expireDate = shareDO.getExpireDate();
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
        if (StringUtils.isEmpty(order)) {
            query.with(new Sort(Sort.Direction.DESC, "createDate"));
        } else {
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if ("descending".equals(order)) {
                direction = Sort.Direction.DESC;
            }
            query.with(new Sort(direction, sortableProp));
        }
        return mongoTemplate.find(query, ShareDO.class, COLLECTION_NAME);
    }

    private ShareDO findByFileId(String fileId){
        Query query = new Query();
        query.addCriteria(Criteria.where("fileId").is(fileId));
        return mongoTemplate.findOne(query, ShareDO.class,COLLECTION_NAME);
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
        return mongoTemplate.count(query,COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> cancelShare(List<String> shareId, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("_id").in(shareId));
        mongoTemplate.remove(query,COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public void deleteAllByUser(List<ConsumerDO> userList) {
        if(userList == null || userList.isEmpty()){
            return;
        }
        userList.stream().forEach(user -> {
            String username = user.getUsername();
            String userId = user.getId();
            Query query = new Query();
            query.addCriteria(Criteria.where("userId").in(userId));
            mongoTemplate.remove(query, COLLECTION_NAME);
        });
    }
}
