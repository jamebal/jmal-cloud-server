package com.jmal.clouddisk.service.impl;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.UserSetting;
import com.jmal.clouddisk.model.UserSettingDTO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * @author jmal
 * @Description 用户设置
 * @Date 2020/11/5 2:51 下午
 */
@Service
public class UserSettingService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String USERID_PARAM = "userId";

    private static final String COLLECTION_NAME = "userSetting";


    /***
     * 更新用户设置
     * @param userSetting UserSetting
     * @return ResponseResult
     */
    public ResponseResult<Object> update(UserSetting userSetting) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userSetting.getUserId()));
        Update update = MongoUtil.getUpdate(userSetting);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 获取用户设置
     * @param userId userId
     * @return ResponseResult
     */
    public ResponseResult<UserSettingDTO> getSetting(String userId) {
        UserSettingDTO userSettingDTO = new UserSettingDTO();
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        UserSetting userSetting = mongoTemplate.findOne(query, UserSetting.class, COLLECTION_NAME);
        if(userSetting != null){
            CglibUtil.copy(userSetting, userSettingDTO);
        }
        return ResultUtil.success(userSettingDTO);
    }

}
