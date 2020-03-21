package com.jmal.clouddisk.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.common.exception.ExceptionType;
import com.jmal.clouddisk.model.Consumer;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Description UserServiceImpl
 * @blame jmal
 */
@Service
public class UserServiceImpl implements IUserService {

    static final String COLLECTION_NAME = "user";

    private Cache<String,String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    IUploadFileService fileService;

    @Override
    public ResponseResult<Object> add(Consumer user) {
        Consumer user1 = getUserInfoByName(user.getUsername());
        if(user1 == null){
            mongoTemplate.save(user, COLLECTION_NAME);
        }
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> delete(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> update(Consumer user, MultipartFile blobAvatar) {
        Query query = new Query();
        String userId = user.getId();
        if(!StringUtils.isEmpty(userId)){
            query.addCriteria(Criteria.where("_id").is(userId));
        }else{
            String name = user.getUsername();
            if(!StringUtils.isEmpty(name)){
                query.addCriteria(Criteria.where("username").is(name));
            }else{
                return ResultUtil.success();
            }
        }
        Update update = new Update();
        String showName = user.getShowName();
        if(!StringUtils.isEmpty(showName)){
            update.set("showName", showName);
        }
        Integer quota = user.getQuota();
        if(quota != null){
            update.set("quota", quota);
        }
        String slogan = user.getSlogan();
        if(!StringUtils.isEmpty(slogan)){
            update.set("slogan", slogan);
        }
        String introduction = user.getIntroduction();
        if(!StringUtils.isEmpty(introduction)){
            update.set("introduction", introduction);
        }else{
            update.set("introduction", "");
        }
        String fileId = "";
        if(blobAvatar != null){
            Consumer consumer = mongoTemplate.findById(userId,Consumer.class,COLLECTION_NAME);
            UploadApiParam upload = new UploadApiParam();
            upload.setUserId(userId);
            upload.setUsername(consumer.getUsername());
            int size = (int) blobAvatar.getSize();
            upload.setFilename("avatar-"+ TimeUntils.getStringTime(System.currentTimeMillis()) +".jpeg");
            upload.setFile(blobAvatar);
            fileId = fileService.uploadConsumerImage(upload);
            update.set("avatar", fileId);
        }
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success(fileId);
    }

    @Override
    public ResponseResult<Object> userInfo(String id, Boolean takeUpSpace) {
        Consumer consumer = mongoTemplate.findById(id,Consumer.class,COLLECTION_NAME);
        consumer.setPassword(null);
        if(takeUpSpace != null && takeUpSpace) {
            consumer.setTakeUpSpace(fileService.takeUpSpace(consumer.getId()));
        }
        return ResultUtil.success(consumer);
    }

    @Override
    public Consumer userInfoById(String userId) {
        if(StringUtils.isEmpty(userId)){
            return null;
        }
        return mongoTemplate.findById(userId,Consumer.class,COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> userList() {
        Query query = new Query();
        List<Consumer> userList = mongoTemplate.find(query,Consumer.class,COLLECTION_NAME);
        return ResultUtil.success(userList);
    }

    @Override
    public String getUserName(String token) {
        if(StringUtils.isEmpty(token)){
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(),ExceptionType.PERMISSION_DENIED.getMsg());
        }
        String username = tokenCache.getIfPresent(token);
        if(StringUtils.isEmpty(username)){
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(),ExceptionType.PERMISSION_DENIED.getMsg());
        }
        return username;
    }

    private Consumer getUserInfoByName(String name){
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(name));
        return mongoTemplate.findOne(query,Consumer.class,COLLECTION_NAME);
    }

}
