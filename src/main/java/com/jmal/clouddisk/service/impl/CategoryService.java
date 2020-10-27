package com.jmal.clouddisk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.Category;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:51 下午
 */
@Service
public class CategoryService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "category";

    /***
     * 分类列表
     * @param userId
     * @return
     */
    public ResponseResult list(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("parentCategoryName").exists(false));
        List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        List<CategoryDTO> categoryDTOList =  categoryList.parallelStream().map(category -> {
            CategoryDTO categoryDTO = new CategoryDTO();
            CglibUtil.copy(category, categoryDTO);
            String categoryName = categoryDTO.getName();
            List<Category> subCategoryList = getSubCategoryList(userId, categoryName);
            categoryDTO.setSubCategorySize(subCategoryList.size());
            return categoryDTO;
        }).sorted().collect(Collectors.toList());
        return ResultUtil.success(categoryDTOList);
    }

    /***
     * 分类树
     * @param userId
     * @return
     */
    public ResponseResult tree(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 查询某个分类的子分类
     * @param userId
     * @param categoryName
     * @return
     */
    private List<Category> getSubCategoryList(String userId, String categoryName){
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("parentCategoryName").is(categoryName));
        return mongoTemplate.find(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 分类信息
     * @param userId
     * @param categoryName
     * @return
     */
    public CategoryDTO categoryInfo(String userId, String categoryName) {
        CategoryDTO categoryDTO = new CategoryDTO();
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("name").is(categoryName));
        Category category = mongoTemplate.findOne(query, Category.class, COLLECTION_NAME);
        if(category != null){
            CglibUtil.copy(category, categoryDTO);
        }
        return categoryDTO;
    }

    /***
     * 添加分类
     * @param categoryDTO
     * @return
     */
    public ResponseResult add(CategoryDTO categoryDTO) {
        CategoryDTO categoryDTO1 = categoryInfo(categoryDTO.getUserId(), categoryDTO.getName());
        if(categoryDTO1 != null){
           return ResultUtil.warning("该分类名称已存在");
        }
        if(StringUtils.isEmpty(categoryDTO.getThumbnailName())){
            categoryDTO.setThumbnailName(categoryDTO.getName());
        }
        Category category = new Category();
        CglibUtil.copy(categoryDTO, category);
        mongoTemplate.save(category);
        return ResultUtil.success();
    }

    /***
     * 更新分类
     * @param categoryDTO
     * @return
     */
    public ResponseResult update(CategoryDTO categoryDTO) {
        if(StringUtils.isEmpty(categoryDTO.getThumbnailName())){
            categoryDTO.setThumbnailName(categoryDTO.getName());
        }
        Category category = new Category();
        CglibUtil.copy(categoryDTO, category);
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(categoryDTO.getUserId()));
        query.addCriteria(Criteria.where("name").is(categoryDTO.getName()));
        Update update = new Update();
        Map<String, Object> categoryDTOMap = BeanUtil.beanToMap(categoryDTO);
        for (Map.Entry<String, Object> objectEntry : categoryDTOMap.entrySet()) {
            update.set(objectEntry.getKey(), objectEntry.getValue());
        }
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除分类
     * @param userId
     * @param categoryName
     * @return
     */
    public ResponseResult delete(String userId, String categoryName) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("name").is(categoryName));
        mongoTemplate.remove(query, COLLECTION_NAME);
        return ResultUtil.success();
    }
}
