package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.CaffeineUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import static com.jmal.clouddisk.service.IUserService.USERNAME;

@Service
@RequiredArgsConstructor
public class CommonUserService {

    private final MongoTemplate mongoTemplate;

    public String getUserNameById(String userId) {
        if (!CharSequenceUtil.isBlank(userId)) {
            String username = CaffeineUtil.getUsernameCache(userId);
            if (CharSequenceUtil.isBlank(username)) {
                ConsumerDO consumer = mongoTemplate.findById(userId, ConsumerDO.class);
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

    public ConsumerDO getUserInfoByUsername(String name) {
        ConsumerDO consumer = CaffeineUtil.getConsumerByUsernameCache(name);
        if (consumer == null) {
            consumer = getUserInfo(name);
            if (consumer != null) {
                CaffeineUtil.setConsumerByUsernameCache(name, consumer);
            }
        }
        return consumer;
    }

    public String getAvatarByUsername(String username) {
        ConsumerDO consumer = getUserInfoByUsername(username);
        if (consumer == null) {
            return "";
        }
        return consumer.getAvatar();
    }

    public ConsumerDO getUserInfo(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERNAME).is(username));
        return mongoTemplate.findOne(query, ConsumerDO.class);
    }

    public ConsumerDO getUserInfoById(String userId) {
        String username = getUserNameById(userId);
        return getUserInfoByUsername(username);
    }

    /**
     * 获取创建者的用户名
     * @return 用户名
     */
    public String getCreatorUsername() {
        Query query = new Query();
        query.addCriteria(Criteria.where("creator").is(true));
        ConsumerDO consumerDO = mongoTemplate.findOne(query, ConsumerDO.class);
        if (consumerDO == null) {
            return null;
        }
        return consumerDO.getUsername();
    }

}
