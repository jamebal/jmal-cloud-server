package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.listener.FileMonitor;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerBase;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author jmal
 * @Description UserServiceImpl
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    public static final String COLLECTION_NAME = "user";

    private final MongoTemplate mongoTemplate;

    private final CommonUserService commonUserService;

    private final UserFileService userFileService;

    private final MessageService messageService;

    private final IShareService shareService;

    private final MenuService menuService;

    private final RoleService roleService;

    private final IAuthDAO authDAO;

    private final FileProperties fileProperties;

    private final UserLoginHolder userLoginHolder;

    private final FileMonitor fileMonitor;

    private final TextEncryptor textEncryptor;

    @Override
    public synchronized ConsumerDO add(ConsumerDTO consumerDTO) {
        String username = consumerDTO.getUsername();
        if (fileProperties.notAllowUsername(username)) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "请使用其他用户名");
        }
        ConsumerDO consumerDO;
        ConsumerDO user1 = getUserInfoByUsername(username);
        if (user1 == null) {
            if (consumerDTO.getQuota() == null) {
                consumerDTO.setQuota(10);
            }
            String originalPwd = consumerDTO.getPassword();
            encryption(consumerDTO, originalPwd);
            consumerDO = new ConsumerDO();
            BeanUtils.copyProperties(consumerDTO, consumerDO);
            consumerDO.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            consumerDO.setId(null);
            // 新建用户目录
            createUserDir(consumerDO.getUsername());
            consumerDO = mongoTemplate.save(consumerDO, COLLECTION_NAME);
            // 更新用户缓存
            CaffeineUtil.setConsumerByUsernameCache(consumerDO.getUsername(), consumerDO);
        } else {
            throw new CommonException(ExceptionType.WARNING.getCode(), "该用户已存在");
        }
        return consumerDO;
    }

    /**
     * 新建用户目录
     *
     * @param username 用户名
     */
    private void createUserDir(String username) {
        // 新建用户目录
        PathUtil.mkdir(Paths.get(fileProperties.getRootDir(), username));
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
            idList = idList.stream().filter(id -> !id.equals(creator.getId())).toList();
            userList.remove(creator);
        }
        // 删除关联文件
        userFileService.deleteAllByUser(userList);
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

        String name = user.getUsername();
        if (fileProperties.notAllowUsername(name)) {
            return ResultUtil.warning("请使用其他用户名");
        }

        Query query = new Query();
        String userId = user.getId();
        ConsumerDO consumerDO;
        if (!CharSequenceUtil.isBlank(userId)) {
            query.addCriteria(Criteria.where("_id").is(userId));
            consumerDO = getUserInfoById(userId);
        } else {
            if (!CharSequenceUtil.isBlank(name)) {
                query.addCriteria(Criteria.where(USERNAME).is(name));
                consumerDO = getUserInfoByUsername(name);
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
            update.set(SHOW_NAME, showName);
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

        Boolean webpDisabled = user.getWebpDisabled();
        if (webpDisabled != null) {
            update.set("webpDisabled", webpDisabled);
            consumerDO.setWebpDisabled(webpDisabled);
        }

        String fileId = "";
        if (!CharSequenceUtil.isBlank(user.getAvatar())) {
            fileId = user.getAvatar();
            update.set("avatar", fileId);
            consumerDO.setAvatar(fileId);
        }
        if (user.getRoles() != null) {
            update.set(RoleService.ROLES, user.getRoles());
            consumerDO.setRoles(user.getRoles());
        }
        // 设置用户头像
        fileId = setConsumerAvatar(blobAvatar, userId, consumerDO, update, fileId);
        LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
        update.set("updateTime", now);
        consumerDO.setUpdateTime(now);
        updateConsumer(userId, query, update);
        if (user.getRoles() != null) {
            // 修改用户角色后更新相关角色用户的权限缓存
            CompletableFuture.runAsync(() -> roleService.updateUserCacheByRole(user.getRoles()));
        }
        return ResultUtil.success(fileId);
    }

    private String setConsumerAvatar(MultipartFile blobAvatar, String userId, ConsumerDO consumerDO, Update update, String fileId) {
        if (blobAvatar != null) {
            ConsumerDO consumer = mongoTemplate.findById(userId, ConsumerDO.class, COLLECTION_NAME);
            if (consumer != null) {
                UploadApiParamDTO upload = new UploadApiParamDTO();
                upload.setUserId(userId);
                upload.setUsername(consumer.getUsername());
                upload.setFilename("avatar-" + System.currentTimeMillis());
                upload.setFile(blobAvatar);
                fileId = userFileService.uploadConsumerImage(upload);
                update.set("avatar", fileId);
                consumerDO.setAvatar(fileId);
            }
        } else {
            // 设置头像文件为public
            userFileService.setPublic(fileId);
        }
        return fileId;
    }

    @Override
    public ResponseResult<ConsumerDTO> userInfo(String id) {
        ConsumerDO consumer = mongoTemplate.findById(id, ConsumerDO.class, COLLECTION_NAME);
        return getConsumerDTOResponseResult(consumer);
    }

    @NotNull
    private ResponseResult<ConsumerDTO> getConsumerDTOResponseResult(ConsumerDO consumer) {
        if (consumer == null) {
            return ResultUtil.success(new ConsumerDTO());
        }
        ConsumerDTO consumerDTO = new ConsumerDTO();
        BeanUtils.copyProperties(consumer, consumerDTO);
        consumerDTO.setTakeUpSpace(messageService.takeUpSpace(consumerDTO.getId()));
        consumerDTO.setPassword(null);
        if (consumerDTO.getAvatar() == null) {
            consumerDTO.setAvatar("");
        }
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class, SettingService.COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDO != null) {
            consumerDTO.setNetdiskName(websiteSettingDO.getNetdiskName());
            consumerDTO.setNetdiskLogo(websiteSettingDO.getNetdiskLogo());
            consumerDTO.setIframe(websiteSettingDO.getIframe());
            consumerDTO.setExactSearch(fileProperties.getExactSearch());
        }
        String newVersion = fileMonitor.hasNewVersion();
        if (!StrUtil.isBlank(newVersion)) {
            consumerDTO.setNewVersion(newVersion);
        }
        return ResultUtil.success(consumerDTO);
    }

    @Override
    public ResponseResult<ConsumerDTO> info() {
        if (StrUtil.isBlank(userLoginHolder.getUsername())) {
            return ResultUtil.success(new ConsumerDTO());
        }
        ConsumerDO consumerDO = getUserInfoByUsername(userLoginHolder.getUsername());
        return getConsumerDTOResponseResult(consumerDO);
    }

    @Override
    public ConsumerDO userInfoById(String userId) {
        return getUserInfoById(userId);
    }

    @Override
    public ResponseResult<List<ConsumerDTO>> userList(QueryUserDTO queryDTO) {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        MongoUtil.commonQuery(queryDTO, query);
        if (!CharSequenceUtil.isBlank(queryDTO.getUsername())) {
            query.addCriteria(Criteria.where(USERNAME).regex(queryDTO.getUsername(), "i"));
        }
        if (!CharSequenceUtil.isBlank(queryDTO.getShowName())) {
            query.addCriteria(Criteria.where(SHOW_NAME).regex(queryDTO.getShowName(), "i"));
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
        }).toList();
        return ResultUtil.success(consumerDTOList).setCount(count);
    }

    @Override
    public List<ConsumerDTO> userListAll() {
        Query query = new Query();
        List<ConsumerDO> userList = mongoTemplate.find(query, ConsumerDO.class);
        return userList.parallelStream().map(consumerDO -> {
            ConsumerDTO consumerDTO = new ConsumerDTO();
            consumerDTO.setUsername(consumerDO.getUsername());
            return consumerDTO;
        }).toList();
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
        LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
        update.set("password", password);
        update.set("updateTime", now);
        updateConsumer(userId, query, update);
    }

    private void updateConsumer(String userId, Query query, Update update) {
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        ConsumerDO consumerDO = commonUserService.getUserInfoByIdNoCache(userId);
        CaffeineUtil.setConsumerByUsernameCache(consumerDO.getUsername(), consumerDO);
    }

    @Override
    public String getUserIdByUserName(String username) {
        return commonUserService.getUserIdByUserName(username);
    }

    @Override
    public String getAvatarByUsername(String username) {
        return commonUserService.getAvatarByUsername(username);
    }

    public String getHashPasswordUserName(String username) {
        if (CharSequenceUtil.isBlank(username)) {
            return null;
        }
        ConsumerDO consumer = getUserInfoByUsername(username);
        if (consumer == null) {
            return null;
        }
        return consumer.getPassword();
    }

    @Override
    public ResponseResult<Boolean> hasUser() {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        if (count > 0) {
            count = 1;
        }
        return ResultUtil.success(true).setCount(count);
    }

    @Override
    public synchronized ResponseResult<Object> initialization(ConsumerDTO consumerDTO) {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        if (count < 1) {
            ConsumerDO user = new ConsumerDO();
            BeanUtils.copyProperties(consumerDTO, user);
            // 首先初始化菜单和角色
            menuService.initMenus();
            roleService.initRoles();
            // 再初始化创建者
            String roleId = roleService.getRoleIdByCode(RoleService.ADMINISTRATORS);
            user.setRoles(Collections.singletonList(roleId));
            user.setCreator(true);
            user.setShowName(user.getUsername());
            user.setQuota((int) (SystemUtil.getFreeSpace() / 2));
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
     *
     * @param user        ConsumerBase
     * @param originalPwd 原始密码
     */
    private static void encryption(ConsumerBase user, String originalPwd) {
        String password = PasswordHash.createHash(originalPwd);
        user.setPassword(password);
    }

    @Override
    public String getUserNameById(String userId) {
        return commonUserService.getUserNameById(userId);
    }

    @Override
    public ConsumerDO getUserInfoByUsername(String name) {
        return commonUserService.getUserInfoByUsername(name);
    }

    @Override
    public boolean isMfaEnabled(String username) {
        ConsumerDO consumerDO = getUserInfoByUsername(username);
        if (consumerDO == null) {
            return false;
        }
        return BooleanUtil.isTrue(consumerDO.getMfaEnabled());
    }

    @Override
    public void enableMfa(String userId, String rawSecret) {
        // 加密密钥
        String encryptedSecret = textEncryptor.encrypt(rawSecret);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        Update update = new Update();
        update.set("mfaSecret", encryptedSecret);
        update.set("mfaEnabled", true);
        updateConsumer(userId, query, update);
    }

    @Override
    public void disableMfa(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        Update update = new Update();
        update.unset("mfaSecret");
        update.unset("mfaEnabled");
        updateConsumer(userId, query, update);
    }

    public ConsumerDO getUserInfoById(String userId) {
        return commonUserService.getUserInfoById(userId);
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
        query.addCriteria(Criteria.where(SHOW_NAME).is(showName));
        ConsumerDO consumerDO = mongoTemplate.findOne(query, ConsumerDO.class, COLLECTION_NAME);
        if (consumerDO != null) {
            return consumerDO.getId();
        }
        return "";
    }

    /**
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

    /**
     * 获取创建者的用户名
     * @return 用户名
     */
    public String getCreatorUsername() {
        return commonUserService.getCreatorUsername();
    }
}
