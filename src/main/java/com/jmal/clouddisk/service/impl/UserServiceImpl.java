package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import cn.hutool.extra.cglib.CglibUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description UserServiceImpl
 */
@Service
public class UserServiceImpl implements IUserService {

    public static final String COLLECTION_NAME = "user";

    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private IFileService fileService;

    @Autowired
    IShareService shareService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private RoleService roleService;

    @Autowired
    IAuthDAO authDAO;

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Override
    public ResponseResult<Object> add(ConsumerDTO consumerDTO) {
        ConsumerDO user1 = getUserInfoByName(consumerDTO.getUsername());
        if (user1 == null) {
            if (consumerDTO.getQuota() == null) {
                consumerDTO.setQuota(10);
            }
            String originalPwd = consumerDTO.getPassword();
            String password = SecureUtil.md5(originalPwd);
            SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, password.getBytes());
            consumerDTO.setEncryptPwd(aes.encryptHex(originalPwd));
            consumerDTO.setPassword(password);
            ConsumerDO consumerDO = new ConsumerDO();
            CglibUtil.copy(consumerDTO, consumerDO);
            consumerDO.setCreateTime(LocalDateTime.now());
            consumerDO.setId(null);
            mongoTemplate.save(consumerDO, COLLECTION_NAME);
        } else {
            return ResultUtil.warning("该用户已存在");
        }
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> delete(List<String> idList) {
        String currentUserId = userLoginHolder.getUserId();
        if(idList.contains(currentUserId)){
            return ResultUtil.warning("不能删除自己");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").in(idList));
        List<ConsumerDO> userList = mongoTemplate.find(query1, ConsumerDO.class, COLLECTION_NAME);
        // 过滤掉创建者
        ConsumerDO creator = userList.stream().filter(user -> user.getCreator() != null && user.getCreator()).findAny().orElse(null);
        if(creator != null){
            idList = idList.stream().filter(id -> !id.equals(creator.getId())).collect(Collectors.toList());
            userList.remove(creator);
        }
        // 删除关联文件
        fileService.deleteAllByUser(userList);
        // 删除关联分享
        shareService.deleteAllByUser(userList);
        // 删除关联token
        authDAO.deleteAllByUser(userList);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(idList));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> update(ConsumerDTO user, MultipartFile blobAvatar) {
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
        if (user.getRoles() != null){
            update.set("roles", user.getRoles());
        }
        if (blobAvatar != null) {
            ConsumerDO consumer = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            if(consumer != null){
                UploadApiParamDTO upload = new UploadApiParamDTO();
                upload.setUserId(userId);
                upload.setUsername(consumer.getUsername());
                upload.setFilename("avatar-" + TimeUntils.getStringTime(System.currentTimeMillis()));
                upload.setFile(blobAvatar);
                fileId = fileService.uploadConsumerImage(upload);
                update.set("avatar", fileId);
            }
        }
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        if(user.getRoles() != null){
            // 修改用户角色后更新相关角色用户的权限缓存
            ThreadUtil.execute(() -> roleService.updateUserCacheByRole(user.getRoles()));
        }
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
    public ResponseResult<List<ConsumerDTO>> userList(QueryUserDTO queryDTO) {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        MongoUtil.commonQuery(queryDTO, query);
        if(!StringUtils.isEmpty(queryDTO.getUsername())){
            query.addCriteria(Criteria.where("username").regex(queryDTO.getUsername(), "i"));
        }
        if(!StringUtils.isEmpty(queryDTO.getShowName())){
            query.addCriteria(Criteria.where("showName").regex(queryDTO.getShowName(), "i"));
        }
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        List<ConsumerDTO> consumerDTOList = userList.parallelStream().map(consumerDO -> {
            ConsumerDTO consumerDTO = new ConsumerDTO();
            CglibUtil.copy(consumerDO, consumerDTO);
            List<String> roleIds = consumerDO.getRoles();
            if(roleIds != null && !roleIds.isEmpty()){
                consumerDTO.setRoleList(roleService.getRoleList(roleIds));
            }
            return consumerDTO;
        }).collect(Collectors.toList());
        return ResultUtil.success(consumerDTOList).setCount(count);
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

    @Override
    public ResponseResult<Object> updatePass(ConsumerDO consumer) {
        String userId = consumer.getId();
        String newPassword = consumer.getPassword();
        if (!StringUtils.isEmpty(userId) && !StringUtils.isEmpty(newPassword)) {
            ConsumerDO consumer1 = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            if(consumer1 != null){
                if (newPassword.equals(consumer1.getPassword())) {
                    return ResultUtil.warning("新密码不能于旧密码相同!");
                }
                Query query = new Query();
                query.addCriteria(Criteria.where("_id").is(userId));
                Update update = new Update();
                String password = SecureUtil.md5(newPassword);
                SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, password.getBytes());
                update.set("encryptPwd", aes.encryptHex(newPassword));
                update.set("password", password);
                mongoTemplate.upsert(query, update, COLLECTION_NAME);
                return ResultUtil.successMsg("修改成功!");
            }
        }
        return ResultUtil.warning("修改失败!");
    }

    @Override
    public ResponseResult<Object> resetPass(ConsumerDO consumer) {
        String userId = consumer.getId();
        if (!StringUtils.isEmpty(userId)) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(userId));
            Update update = new Update();
            String originalPwd = "jmalcloud";
            String password = SecureUtil.md5(originalPwd);
            SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, password.getBytes());
            update.set("encryptPwd", aes.encryptHex(originalPwd));
            update.set("password", password);
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

    @Cacheable(value = "getPasswordByUserName", key = "#username")
    public String getPasswordByUserName(String username) {
        ConsumerDO consumer = getUserInfoByName(username);
        if (consumer == null) {
            return "";
        }
        SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, consumer.getPassword().getBytes());
        return aes.decryptStr(consumer.getEncryptPwd(), CharsetUtil.CHARSET_UTF_8);
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
            // 首先初始化菜单和角色
            menuService.initMenus();
            roleService.initRoles();
            // 再初始化创建者
            String roleId = roleService.getRoleIdByCode(RoleService.ADMINISTRATORS);
            user.setRoles(Collections.singletonList(roleId));
            user.setCreator(true);
            user.setShowName(user.getUsername());
            user.setQuota(15);
            String originalPwd = user.getPassword();
            String password = SecureUtil.md5(originalPwd);
            SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, password.getBytes());
            user.setEncryptPwd(aes.encryptHex(originalPwd));
            user.setPassword(password);
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

    private ConsumerDO getUserInfoByName(String name) {
        Query query = new Query();
        query.addCriteria(Criteria.where("username").is(name));
        return mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
    }

    @Override
    public List<String> getAuthorities(String username) {
        List<String> authorities = new ArrayList<>();
        ConsumerDO consumerDO = getUserInfoByName(username);
        if(consumerDO == null){
            return authorities;
        }
        // 如果是创建者, 直接返回所有权限
        if(consumerDO.getCreator() != null && consumerDO.getCreator()){
            return AnnoManageUtil.AUTHORITIES;
        }
        List<String> roleIdList = consumerDO.getRoles();
        if(roleIdList == null || roleIdList.isEmpty()){
            return authorities;
        }
        return roleService.getAuthorities(roleIdList);
    }

    @Override
    public List<String> getMenuIdList(String userId) {
        List<String> menuIdList = new ArrayList<>();
        ConsumerDO consumerDO = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
        if(consumerDO == null || consumerDO.getRoles() == null){
            return menuIdList;
        }
        if(consumerDO.getCreator() != null && consumerDO.getCreator()){
            // 如果是创建者则返回所有菜单
            return menuService.getAllMenuIdList();
        }
        return roleService.getMenuIdList(consumerDO.getRoles());
    }

    @Override
    public List<String> getUserNameListByRole(String roleId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("roles").is(roleId));
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        return userList.stream().map(ConsumerDO::getUsername).collect(Collectors.toList());
    }

    @Override
    public List<String> getUserNameListByRole(List<String> rolesIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("roles").in(rolesIds));
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        return userList.stream().map(ConsumerDO::getUsername).collect(Collectors.toList());
    }

    @Override
    public boolean getIsCreator(String userId) {
        ConsumerDO consumerDO = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
        if(consumerDO == null){
            return false;
        }
        return consumerDO.getCreator() != null && consumerDO.getCreator();
    }
}
