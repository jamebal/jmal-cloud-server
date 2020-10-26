package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.Category;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:51 下午
 */
@Service
public class CateGoryService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "cateGory";

    /***
     * 分类列表
     * @param userId
     * @return
     */
    public ResponseResult list(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        return ResultUtil.success(categoryList);
    }
}
