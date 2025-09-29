package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.CaffeineUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommonUserService {

    private final ObjectProvider<IUserDAO> userDAOObjectProvider;

    public String getUserNameById(String userId) {
        if (!CharSequenceUtil.isBlank(userId)) {
            String username = CaffeineUtil.getUsernameCache(userId);
            if (CharSequenceUtil.isBlank(username)) {
                ConsumerDO consumer = userDAOObjectProvider.getObject().findById(userId);
                if (consumer != null) {
                    username = consumer.getUsername();
                    CaffeineUtil.setUsernameCache(userId, username);
                    return username;
                }
            } else {
                return username;
            }
        }
        return "";
    }

    public String getUserIdByUserName(String username) {
        ConsumerDO consumer = getUserInfoByUsername(username);
        if (consumer != null) {
            return consumer.getId();
        }
        return null;
    }

    public ConsumerDO getUserInfoByUsername(String username) {
        ConsumerDO consumer = CaffeineUtil.getConsumerByUsernameCache(username);
        if (consumer == null) {
            consumer = getUserInfo(username);
            if (consumer != null) {
                CaffeineUtil.setConsumerByUsernameCache(username, consumer);
            }
        }
        return consumer;
    }

    public String getAvatarByUsername(String username) {
        ConsumerDO consumer = getUserInfoByUsername(username);
        if (consumer == null || CharSequenceUtil.isBlank(consumer.getAvatar())) {
            return "";
        }
        return consumer.getAvatar();
    }

    public ConsumerDO getUserInfo(String username) {
        return userDAOObjectProvider.getObject().findByUsername(username);
    }

    public ConsumerDO getUserInfoByIdNoCache(String userId) {
        return userDAOObjectProvider.getObject().findById(userId);
    }

    public ConsumerDO getUserInfoById(String userId) {
        String username = getUserNameById(userId);
        return getUserInfoByUsername(username);
    }

    public boolean getIsCreator(String userId) {
        ConsumerDO consumerDO = getUserInfoById(userId);
        if (consumerDO == null) {
            return false;
        }
        return consumerDO.getCreator() != null && consumerDO.getCreator();
    }

    /**
     * 获取创建者的用户名
     * @return 用户名
     */
    public String getCreatorUsername() {
        ConsumerDO consumerDO = userDAOObjectProvider.getObject().findOneByCreatorTrue();
        if (consumerDO == null) {
            return null;
        }
        return consumerDO.getUsername();
    }

}
