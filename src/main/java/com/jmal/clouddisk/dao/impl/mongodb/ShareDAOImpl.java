package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class ShareDAOImpl implements IShareDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public ShareDO save(ShareDO share) {
        return mongoTemplate.save(share);
    }

    @Override
    public void updateSubShare(String id, Boolean isPrivacy, String extractionCode, List<String> subShareFileIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("fileId").in(subShareFileIdList));
        Update update = new Update();
        update.set(Constants.FATHER_SHARE_ID, id);
        update.set(Constants.IS_PRIVACY, isPrivacy);
        if (Boolean.TRUE.equals(isPrivacy)) {
            update.set(Constants.EXTRACTION_CODE, extractionCode);
        } else {
            update.unset(Constants.EXTRACTION_CODE);
        }
        mongoTemplate.updateMulti(query, update, ShareDO.class);
    }

    @Override
    public boolean existsByShortId(String shortId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.SHORT_ID).is(shortId));
        return mongoTemplate.exists(query, ShareDO.class);
    }

    @Override
    public ShareDO findByFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).is(fileId));
        return mongoTemplate.findOne(query, ShareDO.class);
    }

    @Override
    public ShareDO findByFatherShareId(String fatherShareId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FATHER_SHARE_ID).is(fatherShareId));
        return mongoTemplate.findOne(query, ShareDO.class);
    }

    @Override
    public ShareDO findById(String shareId) {
        return mongoTemplate.findById(shareId, ShareDO.class);
    }

    @Override
    public boolean existsSubShare(List<String> shareIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FATHER_SHARE_ID).in(shareIdList));
        return mongoTemplate.exists(query, ShareDO.class);
    }

    @Override
    public ShareDO findByShortId(String shortId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.SHORT_ID).is(shortId));
        return mongoTemplate.findOne(query, ShareDO.class);
    }

    @Override
    public Page<ShareDO> findShareList(UploadApiParamDTO upload) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(upload.getUserId()));
        String order = upload.getOrder();
        if (CharSequenceUtil.isBlank(order)) {
            upload.setOrder("createDate");
        }

        long total = countByUserId(upload.getUserId());

        Pageable pageable = upload.getPageable();

        if (total == 0) {
            return Page.empty(pageable);
        }

        query.with(pageable);

        List<ShareDO> content = mongoTemplate.find(query, ShareDO.class);

        return  new PageImpl<>(content, pageable, total);
    }

    private long countByUserId(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        return mongoTemplate.count(query, ShareDO.class);
    }

    @Override
    public void removeByFileIdIn(List<String> fileIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).in(fileIdList));
        mongoTemplate.remove(query, ShareDO.class);
    }

    @Override
    public List<ShareDO> findAllAndRemove(List<String> shareIds) {
        if (shareIds == null || shareIds.isEmpty()) {
            return List.of();
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in((shareIds)));
        return mongoTemplate.findAllAndRemove(query, ShareDO.class);
    }

    @Override
    public void removeByFatherShareId(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FATHER_SHARE_ID).is(id));
        mongoTemplate.remove(query, ShareDO.class);
    }

    @Override
    public void removeByUserId(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").in(userId));
        mongoTemplate.remove(query, ShareDO.class);
    }

}
