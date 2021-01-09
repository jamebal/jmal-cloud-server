package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.crypto.SecureUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description UserServiceImpl
 */
@Service
public class UserServiceImpl implements IUserService {

    static final String COLLECTION_NAME = "user";

    private Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IFileService fileService;

    @Autowired
    private RoleService roleService;

    @Override
    public ResponseResult<Object> add(ConsumerDO user) {
        ConsumerDO user1 = getUserInfoByName(user.getUsername());
        if (user1 == null) {
            if (user.getQuota() == null) {
                user.setQuota(10);
            }
            user.setPassword(SecureUtil.md5(user.getPassword()));
            user.setCreateTime(LocalDateTime.now());
            user.setId(null);
            mongoTemplate.save(user, COLLECTION_NAME);
        } else {
            return ResultUtil.warning("该用户已存在");
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
    public ResponseResult<Object> update(ConsumerDO user, MultipartFile blobAvatar) {
        Query query = new Query();
        String userId = user.getId();
        if (!StringUtils.isEmpty(userId)) {
            query.addCriteria(Criteria.where("_id").is(userId));
        } else {
            String name = user.getUsername();
            if (!StringUtils.isEmpty(name)) {
                query.addCriteria(Criteria.where("username").is(name));
            } else {
                return ResultUtil.success();
            }
        }
        Update update = new Update();
        String showName = user.getShowName();
        if (!StringUtils.isEmpty(showName)) {
            update.set("showName", showName);
        }
        Integer quota = user.getQuota();
        if (quota != null) {
            update.set("quota", quota);
        }
        String slogan = user.getSlogan();
        if (!StringUtils.isEmpty(slogan)) {
            update.set("slogan", slogan);
        }
        String introduction = user.getIntroduction();
        if (!StringUtils.isEmpty(introduction)) {
            update.set("introduction", introduction);
        } else {
            update.set("introduction", "");
        }
        String fileId = "";
        if (!StringUtils.isEmpty(user.getAvatar())) {
            fileId = user.getAvatar();
            update.set("avatar", fileId);
        }
        if (blobAvatar != null) {
            ConsumerDO consumer = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            UploadApiParamDTO upload = new UploadApiParamDTO();
            upload.setUserId(userId);
            upload.setUsername(consumer.getUsername());
            upload.setFilename("avatar-" + TimeUntils.getStringTime(System.currentTimeMillis()) + ".jpeg");
            upload.setFile(blobAvatar);
            fileId = fileService.uploadConsumerImage(upload);
            update.set("avatar", fileId);
        }
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success(fileId);
    }

    @Override
    public ResponseResult<Object> userInfo(String id, Boolean takeUpSpace, Boolean returnPassword) {
        ConsumerDO consumer = mongoTemplate.findById(id, ConsumerDO.class, COLLECTION_NAME);
        if (consumer == null) {
            return ResultUtil.success(new ConsumerDO());
        }
        if (takeUpSpace != null && takeUpSpace) {
            consumer.setTakeUpSpace(fileService.takeUpSpace(consumer.getId()));
        }
        if (returnPassword == null || !returnPassword) {
            consumer.setPassword(null);
        }
        if (consumer.getAvatar() == null) {
            consumer.setAvatar("");
        }
        return ResultUtil.success(consumer);
    }

    @Override
    public ConsumerDO userInfoById(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return null;
        }
        return mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> userList() {
        Query query = new Query();
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        return ResultUtil.success(userList);
    }

    @Override
    public String getUserName(String token) {
        if (StringUtils.isEmpty(token)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(), ExceptionType.PERMISSION_DENIED.getMsg());
        }
        String username = tokenCache.getIfPresent(token);
        if (StringUtils.isEmpty(username)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(), ExceptionType.PERMISSION_DENIED.getMsg());
        }
        return username;
    }

    /***
     * 修改密码
     * @param consumer
     * @return
     */
    @Override
    public ResponseResult<Object> updatePass(ConsumerDO consumer) {
        String userId = consumer.getId();
        String newPassword = consumer.getPassword();
        if (!StringUtils.isEmpty(userId) && !StringUtils.isEmpty(newPassword)) {
            ConsumerDO consumer1 = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            String oldPassword = consumer1.getPassword();
            if (newPassword.equals(oldPassword)) {
                return ResultUtil.warning("新密码不能于旧密码相同!");
            }
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(userId));
            Update update = new Update();
            update.set("password", SecureUtil.md5(newPassword));
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
            return ResultUtil.successMsg("修改成功!");
        }
        return ResultUtil.warning("修改失败!");
    }

    /***
     * 重置密码
     * @param consumer
     * @return
     */
    @Override
    public ResponseResult<Object> resetPass(ConsumerDO consumer) {
        String userId = consumer.getId();
        if (!StringUtils.isEmpty(userId)) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(userId));
            Update update = new Update();
            update.set("password", SecureUtil.md5("jmalcloud"));
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
            return ResultUtil.successMsg("重置密码成功!");
        }
        return ResultUtil.error("重置失败!");
    }

    @Cacheable(value = "getUserIdByUserName", key = "#username")
    @Override
    public String getUserIdByUserName(String username) {
        ConsumerDO consumer = getUserInfoByName(username);
        if (consumer != null) {
            return consumer.getId();
        }
        return null;
    }

    @Override
    public ResponseResult<Boolean> hasUser() {
        Query query = new Query();
        return ResultUtil.success(true).setCount(Convert.toInt(mongoTemplate.count(query, COLLECTION_NAME)));
    }

    @Override
    public ResponseResult<Object> initialization(ConsumerDO user) {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        if (count < 1) {
            user.setRoles(Arrays.asList("Administrators"));
            user.setShowName(user.getUsername());
            user.setQuota(15);
            user.setPassword(SecureUtil.md5(user.getPassword()));
            user.setCreateTime(LocalDateTime.now());
            user.setId(null);
            mongoTemplate.save(user, COLLECTION_NAME);
        }
        return ResultUtil.success();
    }

    @Override
    public String getUserNameById(String userId) {
        if (!StringUtils.isEmpty(userId)) {
            ConsumerDO consumer = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            if (consumer != null) {
                return consumer.getUsername();
            }
        }
        return null;
    }

    @Override
    public void disabledWebp(String userId, Boolean disabled) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        Update update = new Update();
        update.set("webpDisabled", disabled);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    @Override
    public boolean getDisabledWebp(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        ConsumerDO consumer = mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
        if(consumer != null && consumer.getWebpDisabled() != null){
            return consumer.getWebpDisabled();
        }
        return false;
    }

    @Override
    public List<String> getCurrentUserAuthorities() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes.getRequest();
        return null;
    }

    private ConsumerDO getUserInfoByName(String name) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(name));
        return mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
    }

    /***
     * 获取该用户的权限信息
     * @param username
     * @return
     */
    public List<String> getAuthorities(String username) {
        List<String> authorities = new ArrayList<>();
        ConsumerDO consumerDO = getUserInfoByName(username);
        if(consumerDO == null){
            return authorities;
        }
        List<String> roleIdList = consumerDO.getRoles();
        if(roleIdList.isEmpty()){
            return authorities;
        }
        return roleService.getAuthorities(roleIdList);
    }
}
