package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.DES;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerBase;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@Slf4j
public class UserServiceImpl implements IUserService {

    public static final String COLLECTION_NAME = "user";

    public static final String ROLES = "roles";

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
    FileProperties fileProperties;

    @Autowired
    private UserLoginHolder userLoginHolder;

    @PostConstruct
    public void forOlderVersions() {
        // TODO 以后删掉, 适配老版本, 2021-12-31 15:50:22之前的
        Query query = new Query();
        query.addCriteria(Criteria.where("password").not().regex("^1000:"));
        List<ConsumerDO> list = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        if (list.isEmpty()) {
            return;
        }
        list.forEach(user -> {
            String password = user.getPassword();
            String encrypt = user.getEncryptPwd();
            DES des = new DES(Mode.CTS, Padding.PKCS5Padding, password.getBytes(), "01020304".getBytes());
            String originalPwd = des.decryptStr(encrypt, CharsetUtil.CHARSET_UTF_8);
            String newPassword = PasswordHash.createHash(originalPwd);
            String key = newPassword.split(":")[2];
            DES des1 = new DES(Mode.CTS, Padding.PKCS5Padding, key.getBytes(), key.substring(0, 8).getBytes());
            user.setEncryptPwd(des1.encryptHex(originalPwd));
            user.setPassword(newPassword);
            Query query1 = new Query();
            query1.addCriteria(Criteria.where("_id").is(user.getId()));
            Update update = new Update();
            update.set("encryptPwd", des1.encryptHex(originalPwd));
            update.set("password", newPassword);
            mongoTemplate.updateFirst(query1, update, COLLECTION_NAME);
        });

    }

    @Override
    public ResponseResult<Object> add(ConsumerDTO consumerDTO) {
        ConsumerDO user1 = getUserInfoByName(consumerDTO.getUsername());
        if (user1 == null) {
            if (consumerDTO.getQuota() == null) {
                consumerDTO.setQuota(10);
            }
            String originalPwd = consumerDTO.getPassword();
            if (originalPwd.length() < 8) {
                return ResultUtil.warning("密码长度不能少于8位");
            }
            encryption(consumerDTO, originalPwd);
            ConsumerDO consumerDO = new ConsumerDO();
            BeanUtils.copyProperties(consumerDTO, consumerDO);
            consumerDO.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            consumerDO.setId(null);
            // 新建用户目录
            createUserDir(consumerDO.getUsername());
            mongoTemplate.save(consumerDO, COLLECTION_NAME);
            // 更新用户缓存
            CaffeineUtil.setConsumerByUsernameCache(consumerDO.getUsername(), consumerDO);
        } else {
            return ResultUtil.warning("该用户已存在");
        }
        return ResultUtil.success();
    }

    /**
     * 新建用户目录
     * @param username 用户名
     */
    private void createUserDir(String username) {
        // 新建用户目录
        Path path = Paths.get(fileProperties.getRootDir(), username);
        try {
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseResult<Object> delete(List<String> idList) {
        String currentUserId = userLoginHolder.getUserId();
        if (idList.contains(currentUserId)) {
            return ResultUtil.warning("不能删除自己");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").in(idList));
        List<ConsumerDO> userList = mongoTemplate.find(query1, ConsumerDO.class, COLLECTION_NAME);
        // 过滤掉创建者
        ConsumerDO creator = userList.stream().filter(user -> user.getCreator() != null && user.getCreator()).findAny().orElse(null);
        if (creator != null) {
            idList = idList.stream().filter(id -> !id.equals(creator.getId())).collect(Collectors.toList());
            userList.remove(creator);
        }
        // 删除关联文件
        fileService.deleteAllByUser(userList);
        // 删除关联分享
        shareService.deleteAllByUser(userList);
        // 删除关联token
        authDAO.deleteAllByUser(userList);
        // 删除用户缓存
        CaffeineUtil.removeConsumerListByUsernameCache(userList);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(idList));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> update(ConsumerDTO user, MultipartFile blobAvatar) {
        Query query = new Query();
        String userId = user.getId();
        ConsumerDO consumerDO;
        if (!CharSequenceUtil.isBlank(userId)) {
            query.addCriteria(Criteria.where("_id").is(userId));
            consumerDO = getUserInfoById(userId);
        } else {
            String name = user.getUsername();
            if (!CharSequenceUtil.isBlank(name)) {
                query.addCriteria(Criteria.where("username").is(name));
                consumerDO = getUserInfoByName(name);
            } else {
                return ResultUtil.success();
            }
        }
        if (consumerDO == null) {
            consumerDO = new ConsumerDO();
        }
        Update update = new Update();
        String showName = user.getShowName();
        if (!CharSequenceUtil.isBlank(showName)) {
            update.set("showName", showName);
            consumerDO.setShowName(showName);
        }
        Integer quota = user.getQuota();
        if (quota != null) {
            update.set("quota", quota);
            consumerDO.setQuota(quota);
        }
        String slogan = user.getSlogan();
        if (!CharSequenceUtil.isBlank(slogan)) {
            update.set("slogan", slogan);
            consumerDO.setSlogan(slogan);
        }
        String introduction = user.getIntroduction();
        if (!CharSequenceUtil.isBlank(introduction)) {
            update.set("introduction", introduction);
        } else {
            update.set("introduction", "");
        }
        consumerDO.setIntroduction(introduction);
        String fileId = "";
        if (!CharSequenceUtil.isBlank(user.getAvatar())) {
            fileId = user.getAvatar();
            update.set("avatar", fileId);
            consumerDO.setAvatar(fileId);
        }
        if (user.getRoles() != null) {
            update.set(ROLES, user.getRoles());
            consumerDO.setRoles(user.getRoles());
        }
        if (blobAvatar != null) {
            ConsumerDO consumer = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            if (consumer != null) {
                UploadApiParamDTO upload = new UploadApiParamDTO();
                upload.setUserId(userId);
                upload.setUsername(consumer.getUsername());
                upload.setFilename("avatar-" + System.currentTimeMillis());
                upload.setFile(blobAvatar);
                fileId = fileService.uploadConsumerImage(upload);
                update.set("avatar", fileId);
                consumerDO.setAvatar(fileId);
            }
        }
        LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
        update.set("updateTime", now);
        consumerDO.setUpdateTime(now);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        // 更新用户缓存
        CaffeineUtil.setConsumerByUsernameCache(consumerDO.getUsername(), consumerDO);
        if (user.getRoles() != null) {
            // 修改用户角色后更新相关角色用户的权限缓存
            ThreadUtil.execute(() -> roleService.updateUserCacheByRole(user.getRoles()));
        }
        return ResultUtil.success(fileId);
    }

    @Override
    public ResponseResult<ConsumerDTO> userInfo(String id) {
        ConsumerDO consumer = mongoTemplate.findById(id, ConsumerDO.class, COLLECTION_NAME);
        if (consumer == null) {
            return ResultUtil.success(new ConsumerDTO());
        }
        ConsumerDTO consumerDTO = new ConsumerDTO();
        BeanUtils.copyProperties(consumer, consumerDTO);
        consumerDTO.setTakeUpSpace(fileService.takeUpSpace(consumerDTO.getId()));
        consumerDTO.setPassword(null);
        consumerDTO.setEncryptPwd(null);
        if (consumerDTO.getAvatar() == null) {
            consumerDTO.setAvatar("");
        }
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class, SettingService.COLLECTION_NAME_WEBSITE_SETTING);
        if(websiteSettingDO != null) {
            consumerDTO.setNetdiskName(websiteSettingDO.getNetdiskName());
            consumerDTO.setNetdiskLogo(websiteSettingDO.getNetdiskLogo());
        }
        return ResultUtil.success(consumerDTO);
    }

    @Override
    public ConsumerDO userInfoById(String userId) {
        if (CharSequenceUtil.isBlank(userId)) {
            return null;
        }
        return mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
    }

    @Override
    public ResponseResult<List<ConsumerDTO>> userList(QueryUserDTO queryDTO) {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        MongoUtil.commonQuery(queryDTO, query);
        if (!CharSequenceUtil.isBlank(queryDTO.getUsername())) {
            query.addCriteria(Criteria.where("username").regex(queryDTO.getUsername(), "i"));
        }
        if (!CharSequenceUtil.isBlank(queryDTO.getShowName())) {
            query.addCriteria(Criteria.where("showName").regex(queryDTO.getShowName(), "i"));
        }
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        List<ConsumerDTO> consumerDTOList = userList.parallelStream().map(consumerDO -> {
            ConsumerDTO consumerDTO = new ConsumerDTO();
            BeanUtils.copyProperties(consumerDO, consumerDTO);
            List<String> roleIds = consumerDO.getRoles();
            if (roleIds != null && !roleIds.isEmpty()) {
                consumerDTO.setRoleList(roleService.getRoleList(roleIds));
            }
            return consumerDTO;
        }).collect(Collectors.toList());
        return ResultUtil.success(consumerDTOList).setCount(count);
    }

    @Override
    public List<ConsumerDTO> userListAll() {
        Query query = new Query();
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        return userList.parallelStream().map(consumerDO -> {
            ConsumerDTO consumerDTO = new ConsumerDTO();
            consumerDTO.setPassword(decrypt(consumerDO));
            consumerDTO.setUsername(consumerDO.getUsername());
            return consumerDTO;
        }).collect(Collectors.toList());
    }

    @Override
    public String getUserName(String token) {
        if (CharSequenceUtil.isBlank(token)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(), ExceptionType.PERMISSION_DENIED.getMsg());
        }
        String username = tokenCache.getIfPresent(token);
        if (CharSequenceUtil.isBlank(username)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED.getCode(), ExceptionType.PERMISSION_DENIED.getMsg());
        }
        return username;
    }

    @Override
    public ResponseResult<Object> updatePass(ConsumerDO consumer) {
        String userId = consumer.getId();
        String newPassword = consumer.getPassword();
        if (!CharSequenceUtil.isBlank(userId) && !CharSequenceUtil.isBlank(newPassword)) {
            ConsumerDO consumer1 = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            if (consumer1 != null) {
                if (newPassword.equals(consumer1.getPassword())) {
                    return ResultUtil.warning("新密码不能与旧密码相同!");
                }
                updatePwd(userId, newPassword);
                return ResultUtil.successMsg("修改成功!");
            }
        }
        return ResultUtil.warning("修改失败!");
    }

    @Override
    public ResponseResult<Object> resetPass(ConsumerDO consumer) {
        String userId = consumer.getId();
        if (!CharSequenceUtil.isBlank(userId)) {
            String originalPwd = "jmalcloud";
            // password 1000:0b69ec810783195a102a73c12d4794c29d06904de2f95da1:37c6a397accb83909dc1d15824b8ffb6010649aad9567e99
            // encryptPwd 919a454fd838b5f036b760537b10d14a
            updatePwd(userId, originalPwd);
            return ResultUtil.successMsg("重置密码成功!");
        }
        return ResultUtil.error("重置失败!");
    }

    private void updatePwd(String userId, String originalPwd) {
        if (originalPwd.length() < 8) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "密码长度不能少于8位");
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        Update update = new Update();
        String password = PasswordHash.createHash(originalPwd);
        String key = password.split(":")[2];
        DES des = new DES(Mode.CTS, Padding.PKCS5Padding, key.getBytes(), key.substring(0, 8).getBytes());
        String encryptPwd = des.encryptHex(originalPwd);
        LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
        update.set("encryptPwd", encryptPwd);
        update.set("password", password);
        update.set("updateTime", now);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        // 更新用户缓存
        ConsumerDO consumerDO = getUserInfoById(userId);
        if (consumerDO != null) {
            consumerDO.setEncryptPwd(encryptPwd);
            consumerDO.setPassword(password);
            consumerDO.setUpdateTime(now);
            CaffeineUtil.setConsumerByUsernameCache(consumerDO.getUsername(), consumerDO);
        }
    }

    @Override
    public String getUserIdByUserName(String username) {
        ConsumerDO consumer = getUserInfoByName(username);
        if (consumer != null) {
            return consumer.getId();
        }
        return null;
    }

    public String getShowNameByUserUsername(String username) {
        ConsumerDO consumer = getUserInfoByName(username);
        if (consumer == null) {
            return "";
        }
        return consumer.getShowName();
    }

    public String getPasswordByUserName(String username) {
        ConsumerDO consumer = getUserInfoByName(username);
        if (consumer == null) {
            return "";
        }
        return decrypt(consumer);
    }

    public static String decrypt(ConsumerDO consumer) {
        String password = consumer.getPassword();
        String key = password.split(":")[2];
        DES des = new DES(Mode.CTS, Padding.PKCS5Padding, key.getBytes(), key.substring(0, 8).getBytes());
        return des.decryptStr(consumer.getEncryptPwd(), CharsetUtil.CHARSET_UTF_8);
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
            user.setQuota((int) (SystemUtil.getFreeSpace()/2));
            String originalPwd = user.getPassword();
            encryption(user, originalPwd);
            user.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            user.setId(null);
            // 新建用户目录
            createUserDir(user.getUsername());
            mongoTemplate.save(user, COLLECTION_NAME);
        }
        return ResultUtil.success();
    }

    /**
     * 加密
     * @param user ConsumerBase
     * @param originalPwd 原始密码
     */
    private static void encryption(ConsumerBase user, String originalPwd) {
        String password = PasswordHash.createHash(originalPwd);
        String key = password.split(":")[2];
        DES des = new DES(Mode.CTS, Padding.PKCS5Padding, key.getBytes(), key.substring(0, 8).getBytes());
        user.setEncryptPwd(des.encryptHex(originalPwd));
        user.setPassword(password);
    }

    @Override
    public String getUserNameById(String userId) {
        if (!CharSequenceUtil.isBlank(userId)) {
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
        if (consumer != null && consumer.getWebpDisabled() != null) {
            return consumer.getWebpDisabled();
        }
        return true;
    }

    private ConsumerDO getUserInfoByName(String name) {
        ConsumerDO consumer = CaffeineUtil.getConsumerByUsernameCache(name);
        if (consumer == null) {
            Query query = new Query();
            query.addCriteria(Criteria.where("username").is(name));
            consumer = mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
            if (consumer != null) {
                CaffeineUtil.setConsumerByUsernameCache(name, consumer);
            }
        }
        return consumer;
    }

    public ConsumerDO getUserInfoById(String userId) {
        return mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
    }

    @Override
    public List<String> getAuthorities(String username) {
        List<String> authorities = new ArrayList<>();
        ConsumerDO consumerDO = getUserInfoByName(username);
        if (consumerDO == null) {
            return authorities;
        }
        // 如果是创建者, 直接返回所有权限
        if (consumerDO.getCreator() != null && consumerDO.getCreator()) {
            return AnnoManageUtil.AUTHORITIES;
        }
        List<String> roleIdList = consumerDO.getRoles();
        if (roleIdList == null || roleIdList.isEmpty()) {
            return authorities;
        }
        return roleService.getAuthorities(roleIdList);
    }

    @Override
    public List<String> getMenuIdList(String userId) {
        List<String> menuIdList = new ArrayList<>();
        ConsumerDO consumerDO = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
        if (consumerDO == null || consumerDO.getRoles() == null) {
            return menuIdList;
        }
        if (consumerDO.getCreator() != null && consumerDO.getCreator()) {
            // 如果是创建者则返回所有菜单
            return menuService.getAllMenuIdList();
        }
        return roleService.getMenuIdList(consumerDO.getRoles());
    }

    @Override
    public List<String> getUserNameListByRole(String roleId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(ROLES).is(roleId));
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        return userList.stream().map(ConsumerDO::getUsername).collect(Collectors.toList());
    }

    @Override
    public List<String> getUserNameListByRole(List<String> rolesIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where(ROLES).in(rolesIds));
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class, COLLECTION_NAME);
        return userList.stream().map(ConsumerDO::getUsername).collect(Collectors.toList());
    }

    @Override
    public boolean getIsCreator(String userId) {
        ConsumerDO consumerDO = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
        if (consumerDO == null) {
            return false;
        }
        return consumerDO.getCreator() != null && consumerDO.getCreator();
    }

    @Override
    public String getUserIdByShowName(String showName) {
        Query query = new Query();
        query.addCriteria(Criteria.where("showName").is(showName));
        ConsumerDO consumerDO = mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
        if (consumerDO != null) {
            return consumerDO.getId();
        }
        return "";
    }

    @Override
    public String getShowNameById(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        ConsumerDO consumerDO = mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
        if (consumerDO != null) {
            return consumerDO.getShowName();
        }
        return "";
    }

    /***
     * 获取创建者的头像
     * @return 头像文件Id
     */
    public String getCreatorAvatar() {
        Query query = new Query();
        query.addCriteria(Criteria.where("creator").is(true));
        ConsumerDO consumerDO = mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
        if (consumerDO == null) {
            return null;
        }
        return consumerDO.getAvatar();
    }
}
