package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IAccessTokenDAO;
import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.dao.mapping.CommonField;
import com.jmal.clouddisk.dao.mapping.UserField;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.listener.FileMonitor;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerBase;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.model.rbac.Personalization;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author jmal
 * @Description UserServiceImpl
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    public static final String COLLECTION_NAME = "user";

    private final IUserDAO userDAO;

    private final IWebsiteSettingDAO websiteSettingDAO;

    private final CommonUserService commonUserService;

    private final UserFileService userFileService;

    private final MessageService messageService;

    private final IShareService shareService;

    private final RoleService roleService;

    private final IAccessTokenDAO accessTokenDAO;

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
            consumerDO.setCreatedTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            consumerDO.setId(null);
            // 新建用户目录
            createUserDir(consumerDO.getUsername());
            consumerDO = userDAO.save(consumerDO);
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
        List<ConsumerDO> userList = userDAO.findAllById(idList);
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
        accessTokenDAO.deleteAllByUser(userList);
        // 删除用户缓存
        CaffeineUtil.removeConsumerListByUsernameCache(userList);
        userDAO.deleteAllById(idList);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> update(ConsumerDTO user, MultipartFile blobAvatar) {

        String name = user.getUsername();
        if (fileProperties.notAllowUsername(name)) {
            return ResultUtil.warning("请使用其他用户名");
        }

        MyQuery query = new MyQuery();
        String userId = user.getId();
        ConsumerDO consumerDO;
        if (!CharSequenceUtil.isBlank(userId)) {
            query.eq(CommonField.ID.getLogical(), userId);
            consumerDO = getUserInfoById(userId);
        } else {
            if (!CharSequenceUtil.isBlank(name)) {
                query.eq(USERNAME, name);
                consumerDO = getUserInfoByUsername(name);
            } else {
                return ResultUtil.success();
            }
        }
        if (consumerDO == null) {
            consumerDO = new ConsumerDO();
        }
        MyUpdate update = new MyUpdate();
        String showName = user.getShowName();
        if (!CharSequenceUtil.isBlank(showName)) {
            update.set(UserField.SHOW_NAME.getLogical(), showName);
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
            update.set(UserField.WEBP_DISABLED.getLogical(), webpDisabled);
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
        update.set(UserField.UPDATED_TIME.getLogical(), now);
        consumerDO.setUpdatedTime(now);
        updateConsumer(userId, query, update);
        if (user.getRoles() != null) {
            // 修改用户角色后更新相关角色用户的权限缓存
            Completable.fromAction(() -> roleService.updateUserCacheByRole(user.getRoles()))
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        }
        return ResultUtil.success(fileId);
    }

    private String setConsumerAvatar(MultipartFile blobAvatar, String userId, ConsumerDO consumerDO, MyUpdate update, String fileId) {
        if (blobAvatar != null) {
            ConsumerDO consumer = userDAO.findById(userId);
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
        ConsumerDO consumer = userDAO.findById(id);
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
        WebsiteSettingDO websiteSettingDO = websiteSettingDAO.findOne();
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
        Page<ConsumerDO> consumerDOPage = userDAO.findUserList(queryDTO);
        List<ConsumerDO> userList = consumerDOPage.getContent();
        List<ConsumerDTO> consumerDTOList = userList.parallelStream().map(consumerDO -> {
            ConsumerDTO consumerDTO = new ConsumerDTO();
            BeanUtils.copyProperties(consumerDO, consumerDTO);
            List<String> roleIds = consumerDO.getRoles();
            if (roleIds != null && !roleIds.isEmpty()) {
                consumerDTO.setRoleList(roleService.getRoleList(roleIds));
            }
            return consumerDTO;
        }).toList();
        return ResultUtil.success(consumerDTOList).setCount(consumerDOPage.getTotalElements());
    }

    @Override
    public ResponseResult<Object> updatePass(ConsumerDO consumer) {
        String userId = consumer.getId();
        String newPassword = consumer.getPassword();
        if (!CharSequenceUtil.isBlank(userId) && !CharSequenceUtil.isBlank(newPassword)) {
            ConsumerDO consumer1 = userDAO.findById(userId);
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
        MyQuery query = new MyQuery();
        query.eq(CommonField.ID.getLogical(), userId);
        MyUpdate update = new MyUpdate();
        String password = PasswordHash.createHash(originalPwd);
        LocalDateTime now = LocalDateTime.now(TimeUntils.ZONE_ID);
        update.set("password", password);
        update.set(UserField.UPDATED_TIME.getLogical(), now);
        updateConsumer(userId, query, update);
    }

    private void updateConsumer(String userId, MyQuery query, MyUpdate update) {
        userDAO.upsert(query, update);
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
        long count = userDAO.count();
        if (count > 0) {
            count = 1;
        }
        return ResultUtil.success(true).setCount(count);
    }

    @Override
    public synchronized ResponseResult<Object> initialization(ConsumerDTO consumerDTO) {
        long count = userDAO.count();
        if (count < 1) {
            ConsumerDO user = new ConsumerDO();
            BeanUtils.copyProperties(consumerDTO, user);
            // 再初始化创建者
            String roleId = roleService.getRoleIdByCode(RoleService.ADMINISTRATORS);
            user.setRoles(Collections.singletonList(roleId));
            user.setCreator(true);
            user.setShowName(user.getUsername());
            user.setQuota((int) (SystemUtil.getFreeSpace() / 2));
            String originalPwd = user.getPassword();
            encryption(user, originalPwd);
            user.setCreatedTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            user.setId(null);
            // 新建用户目录
            createUserDir(user.getUsername());
            userDAO.save(user);
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
        MyQuery query = new MyQuery();
        query.eq(CommonField.ID.getLogical(), userId);
        MyUpdate update = new MyUpdate();
        update.set(UserField.MFA_SECRET.getLogical(), encryptedSecret);
        update.set(UserField.MFA_ENABLED.getLogical(), true);
        updateConsumer(userId, query, update);
    }

    @Override
    public void disableMfa(String userId) {
        MyQuery query = new MyQuery();
        query.eq(CommonField.ID.getLogical(), userId);
        MyUpdate update = new MyUpdate();
        update.unset(UserField.MFA_SECRET.getLogical());
        update.unset(UserField.MFA_ENABLED.getLogical());
        updateConsumer(userId, query, update);
    }

    @Override
    public String getUsername(String userId) {
        return userDAO.getUsernameById(userId);
    }

    public ConsumerDO getUserInfoById(String userId) {
        return commonUserService.getUserInfoById(userId);
    }

    @Override
    public String getUserIdByShowName(String showName) {
        ConsumerDO consumerDO = userDAO.findByShowName(showName);
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
        ConsumerDO consumerDO = userDAO.findOneByCreatorTrue();
        if (consumerDO == null) {
            return null;
        }
        return consumerDO.getAvatar();
    }

    public void resetAdminPassword() {
        if (BooleanUtil.isTrue(fileProperties.getResetAdminPassword())) {
            ConsumerDO consumer = userDAO.findOneByCreatorTrue();
            if (consumer == null) {
                return;
            }
            if (BooleanUtil.isTrue(consumer.getCreator())) {
                // 生成密码
                String randomPass = Base64.encodeUrlSafe(Convert.toStr(RandomUtil.randomInt(100000, 1000000)));
                String hash = PasswordHash.createHash(randomPass);
                if (userDAO.resetAdminPassword(hash)) {
                    log.warn("管理员: {}, 密码已重置为: {}，请请务必将环境变量'RESET_ADMIN_PASSWORD'移除或设置为false！", consumer.getUsername(), randomPass);
                }
                consumer.setPassword(hash);
                CaffeineUtil.setConsumerByUsernameCache(consumer.getUsername(), consumer);
            }
        }
    }

    public Personalization getPersonalization(String username) {
        return java.util.Optional.ofNullable(getUserInfoByUsername(username))
                .map(ConsumerDO::getPersonalization)
                .orElseGet(Personalization::new);
    }

    public void savePersonalization(String username, Personalization personalization) {
        ConsumerDO consumerDO = getUserInfoByUsername(username);
        if (consumerDO != null) {
            MyQuery query = new MyQuery();
            query.eq(CommonField.ID.getLogical(), consumerDO.getId());
            MyUpdate update = new MyUpdate();
            update.set("personalization", personalization);
            updateConsumer(consumerDO.getId(), query, update);
        }
    }
}
