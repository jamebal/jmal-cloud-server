package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.DirectLink;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class DirectLinkService {

    public static final String COLLECTION_NAME = "direct_links";
    public static final String MARK = "mark";
    private final FileServiceImpl fileService;
    private final UserLoginHolder userLoginHolder;
    private final MongoTemplate mongoTemplate;

    public String createDirectLink(String fileId) {
        String userId = userLoginHolder.getUserId();
        checkOwnership(fileId, userId);
        DirectLink directLink = getDirectLink(fileId);
        if (directLink != null) {
            return directLink.getMark();
        }
        String mark = generateMark();
        Single.create(emitter -> upsertDirectLink(fileId, mark, userId)).subscribeOn(Schedulers.io()).subscribe();
        return mark;
    }

    public String resetDirectLink(String fileId) {
        String userId = userLoginHolder.getUserId();
        checkOwnership(fileId, userId);
        String mark = generateMark();
        Single.create(emitter -> upsertDirectLink(fileId, mark, userId)).subscribeOn(Schedulers.io()).subscribe();
        return mark;
    }

    public String resetAllDirectLink(String fileId) {
        checkOwnership(fileId, userLoginHolder.getUserId());
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userLoginHolder.getUserId()));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return resetDirectLink(fileId);
    }

    public String getFileIdByMark(String mark) {
        Query query = new Query();
        query.addCriteria(Criteria.where(MARK).is(mark));
        DirectLink directLink = mongoTemplate.findOne(query, DirectLink.class, COLLECTION_NAME);
        if (directLink == null) {
            return null;
        }
        return directLink.getFileId();
    }

    private void upsertDirectLink(String fileId, String mark, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).is(fileId));
        Update update = new Update();
        update.set(MARK, mark);
        update.set(Constants.FILE_ID, fileId);
        update.set(IUserService.USER_ID, userId);
        update.set(Constants.UPDATE_DATE, LocalDateTime.now());
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    private String generateMark() {
        String mark;
        while (true) {
            mark = Base64.encodeUrlSafe(new SecureRandom().generateSeed(12));
            if (!isExistsMark(mark)) {
                return mark;
            }
        }
    }

    private boolean isExistsMark(String mark) {
        Query query = new Query();
        query.addCriteria(Criteria.where(MARK).is(mark));
        return mongoTemplate.exists(query, DirectLink.class, COLLECTION_NAME);
    }

    private DirectLink getDirectLink(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).is(fileId));
        return mongoTemplate.findOne(query, DirectLink.class, COLLECTION_NAME);
    }

    /**
     * 检查文件所有权
     * @param fileId 文件id
     * @param userId 用户id
     */
    private void checkOwnership(String fileId, String userId) {
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        if (!fileDocument.getUserId().equals(userId)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
    }

}
