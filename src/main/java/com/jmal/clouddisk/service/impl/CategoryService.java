package com.jmal.clouddisk.service.impl;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.Category;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String USERID_PARAM = "userId";

    private static final String COLLECTION_NAME = "category";

    /***
     * 分类列表
     * @param userId 用户id
     * @return 一级分类列表
     */
    public List<CategoryDTO> list(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        query.addCriteria(Criteria.where("parentCategoryName").exists(false));
        List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        return categoryList.parallelStream().map(category -> {
            CategoryDTO categoryDTO = new CategoryDTO();
            CglibUtil.copy(category, categoryDTO);
            String categoryName = categoryDTO.getName();
            List<Category> subCategoryList = getSubCategoryList(userId, categoryName);
            categoryDTO.setSubCategorySize(subCategoryList.size());
            return categoryDTO;
        }).sorted().collect(Collectors.toList());
    }

    /***
     * 分类树
     * @param userId 用户id
     * @return 分类树结构
     */
    public List<Map<String, Object>> tree(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        List<Category> categoryList = mongoTemplate.find(query, Category.class, COLLECTION_NAME);
        return getSubCategory(null, categoryList);
    }

    /***
     * 查找分类的子集
     * @param categoryName 分类名称
     * @param categoryList 分类列表
     * @return 分类列表
     */
    private List<Map<String, Object>> getSubCategory(String categoryName, List<Category> categoryList) {
        List<Map<String, Object>> categoryTreeMapList = new ArrayList<>();
        List<Category> categoryList1;
        if(StringUtils.isEmpty(categoryName)) {
            categoryList1 = categoryList.stream().filter(category -> StringUtils.isEmpty(category.getParentCategoryName())).sorted().collect(Collectors.toList());
        } else {
            categoryList1 = categoryList.stream().filter(category -> categoryName.equals(category.getParentCategoryName())).sorted().collect(Collectors.toList());
        }
        categoryList1.forEach(category -> {
            Map<String, Object> subCategoryTreeMap = new HashMap<>(16);
            subCategoryTreeMap.put("label", category.getName());
            subCategoryTreeMap.put("value", category.getId());
            subCategoryTreeMap.put("children", getSubCategory(category.getName(), categoryList));
            categoryTreeMapList.add(subCategoryTreeMap);
        });
        return categoryTreeMapList;
    }


    /***
     * 查询某个分类的子分类
     * @param userId 用户id
     * @param categoryName 分类名称
     * @return 分类列表
     */
    private List<Category> getSubCategoryList(String userId, String categoryName){
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        query.addCriteria(Criteria.where("parentCategoryName").is(categoryName));
        return mongoTemplate.find(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param categoryName 分类名称
     * @return 一个分类信息
     */
    public Category getCategoryInfo(String userId, String categoryName) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        query.addCriteria(Criteria.where("name").is(categoryName));
        return mongoTemplate.findOne(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 通过Id获取分类信息
     * @param categoryId 分类id
     * @return 一个分类信息
     */
    private Category getCategoryInfo(String categoryId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryId));
        return mongoTemplate.findOne(query, Category.class, COLLECTION_NAME);
    }

    /***
     * 添加分类
     * @param categoryDTO 参数对象
     * @return ResponseResult
     */
    public ResponseResult<Object> add(CategoryDTO categoryDTO) {
        Category category1 = getCategoryInfo(categoryDTO.getUserId(), categoryDTO.getName());
        if(category1 != null){
           return ResultUtil.warning("该分类名称已存在");
        }
        if(!StringUtils.isEmpty(categoryDTO.getParentCategoryName())) {
            Category parentCategory = getCategoryInfo(categoryDTO.getUserId(), categoryDTO.getParentCategoryName());
            if(parentCategory == null){
                return ResultUtil.warning("该父分类不存在");
            }
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
     * @param categoryDTO 参数对象
     * @return ResponseResult
     */
    public ResponseResult<Object> update(CategoryDTO categoryDTO) {
        Category category1 = getCategoryInfo(categoryDTO.getId());
        if(category1 == null){
            return ResultUtil.warning("该分类不存在");
        }
        if(StringUtils.isEmpty(categoryDTO.getThumbnailName())){
            categoryDTO.setThumbnailName(categoryDTO.getName());
        }
        Category category = new Category();
        CglibUtil.copy(categoryDTO, category);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryDTO.getId()));
        Update update = MongoUtil.getUpdate(category);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除分类
     * @param userId 用户id
     * @param categoryIdList 分类id列表
     */
    public void delete(String userId, List<String> categoryIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        query.addCriteria(Criteria.where("_id").in(categoryIdList));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }
}
