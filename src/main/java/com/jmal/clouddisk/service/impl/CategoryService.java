package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.CategoryDO;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

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
@RequiredArgsConstructor
public class CategoryService {

    private final MongoTemplate mongoTemplate;

    public static final String COLLECTION_NAME = "category";

    /***
     * 分类列表
     * @param userId 用户id
     * @return 一级分类列表
     */
    public List<CategoryDTO> list(String userId, String parentId) {
        List<CategoryDTO> categoryTreeList = tree(userId, true);
        return getSubCategoryList(parentId, categoryTreeList);
    }

    /***
     * 根据分类树查询某个分类的子分类
     * @param parentCategoryId 父分类id
     * @param categoryTreeList 分类数
     * @return 分类列表
     */
    private List<CategoryDTO> getSubCategoryList(String parentCategoryId, List<CategoryDTO> categoryTreeList) {
        List<CategoryDTO> categoryDTOList = new ArrayList<>();
        if (CharSequenceUtil.isBlank(parentCategoryId)) {
            return categoryTreeList;
        }
        for (CategoryDTO categoryDTO : categoryTreeList) {
            if (categoryDTO.getChildren() == null) {
                continue;
            }
            if (parentCategoryId.equals(categoryDTO.getId())) {
                categoryDTOList.addAll(categoryDTO.getChildren());
            } else {
                categoryDTOList.addAll(getSubCategoryList(parentCategoryId, categoryDTO.getChildren()));
            }
        }
        return categoryDTOList;
    }

    /***
     * 获取分类的文章数
     * @param categoryDTO categoryDTO
     */
    private void getCategoryArticlesNum(CategoryDTO categoryDTO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("categoryIds").is(categoryDTO.getId()));
        query.addCriteria(Criteria.where(Constants.RELEASE).is(true));
        long count = mongoTemplate.count(query, FileDocument.class);
        categoryDTO.setArticleNum(Convert.toInt(count));
        categoryDTO.setValue(Convert.toInt(count));
    }

    /***
     * 分类树
     * @param userId 用户id
     * @param statArticleNum 是否统计文章数
     * @return 分类树结构
     */
    public List<CategoryDTO> tree(String userId, boolean statArticleNum) {
        Query query = new Query();
        if(!CharSequenceUtil.isBlank(userId)){
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        } else {
            query.addCriteria(Criteria.where(IUserService.USER_ID).exists(false));
        }
        List<CategoryDO> categoryDOList = mongoTemplate.find(query, CategoryDO.class, COLLECTION_NAME);
        List<CategoryDTO> categoryDTOList = categoryDOList.parallelStream().map(category -> {
            CategoryDTO categoryDTO = new CategoryDTO();
            BeanUtils.copyProperties(category, categoryDTO);
            if (statArticleNum) {
                getCategoryArticlesNum(categoryDTO);
            }
            return categoryDTO;
        }).collect(Collectors.toList());
        Map<String, Integer> articleNumMap = null;
        if (statArticleNum) {
            articleNumMap = new HashMap<>(16);
        }
        List<CategoryDTO> categoryTreeList = getSubCategory(null, categoryDTOList);
        getArticleNum(articleNumMap, categoryTreeList, null);
        setArticleNum(articleNumMap, categoryTreeList);
        return categoryTreeList;
    }

    /**
     * 查找分类的子集
     *
     * @param parentCategoryId 父分类id
     * @param categoryDTOList  分类列表
     * @return 分类列表
     */
    private List<CategoryDTO> getSubCategory(String parentCategoryId, List<CategoryDTO> categoryDTOList) {
        List<CategoryDTO> categoryTreeList = new ArrayList<>();
        List<CategoryDTO> categoryList;
        if (CharSequenceUtil.isBlank(parentCategoryId)) {
            categoryList = categoryDTOList.stream().filter(category -> CharSequenceUtil.isBlank(category.getParentCategoryId())).sorted().collect(Collectors.toList());
        } else {
            categoryList = categoryDTOList.stream().filter(category -> parentCategoryId.equals(category.getParentCategoryId())).collect(Collectors.toList());
        }
        categoryList.forEach(subCategory -> {
            List<CategoryDTO> subList = getSubCategory(subCategory.getId(), categoryDTOList);
            if (!subList.isEmpty()) {
                subCategory.setChildren(subList);
            }
            categoryTreeList.add(subCategory);
        });
        return categoryTreeList;
    }

    /**
     * <p>获取文章数</p>
     * <br>把每个分类的文章数存到articleNumMap里
     * <br>articleNumMap => key:分类id , value:文章数
     * @param articleNumMap 每个分类的文章数
     * @param categoryTreeList 分类树
     * @param categoryId 分类id
     * @return 分类树
     */
    private int getArticleNum(Map<String, Integer> articleNumMap, List<CategoryDTO> categoryTreeList, String categoryId) {
        int count = 0;
        if (articleNumMap == null || categoryTreeList == null) {
            return count;
        }
        for (CategoryDTO category : categoryTreeList) {
            count += category.getArticleNum();
            int curCnt = getArticleNum(articleNumMap, category.getChildren(), category.getId());
            articleNumMap.put(category.getId(), curCnt);
            count += curCnt;
        }
        if(!CharSequenceUtil.isBlank(categoryId)){
            articleNumMap.put(categoryId, count);
        }
        return count;
    }

    /**
     * <p>设置文章数</p>
     * <br>把articleNumMap里的文章数取出来放到categoryTreeList里
     * @param articleNumMap 每个分类的文章数
     * @param categoryTreeList 分类树
     * @return 分类树
     */
    private List<CategoryDTO> setArticleNum(Map<String, Integer> articleNumMap, List<CategoryDTO> categoryTreeList){
        if(articleNumMap == null || categoryTreeList == null){
            return categoryTreeList;
        }
        categoryTreeList = categoryTreeList.stream().peek(category -> {
            if (articleNumMap.containsKey(category.getId())){
                if(category.getChildren() == null || category.getChildren().isEmpty()){
                    category.setValue(category.getValue());
                }
                category.setValue(articleNumMap.get(category.getId()) + category.getValue());
            }
            List<CategoryDTO> subList = category.getChildren();
            if(subList != null && !subList.isEmpty()){
                category.setChildren(setArticleNum(articleNumMap, subList));
            }
        }).collect(Collectors.toList());
        return categoryTreeList;
    }

    /***
     * 根据id查询分类列表
     * @param categoryIds 分类id集合
     * @return 分类列表
     */
    public List<CategoryDO> getCategoryListByIds(Object[] categoryIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(categoryIds));
        return mongoTemplate.find(query, CategoryDO.class, COLLECTION_NAME);
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param categoryName 分类名称
     * @return 一个分类信息
     */
    public CategoryDO getCategoryInfo(String userId, String categoryName) {
        Query query = new Query();
        if (!CharSequenceUtil.isBlank(userId)) {
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        } else {
            query.addCriteria(Criteria.where(IUserService.USER_ID).exists(false));
        }
        query.addCriteria(Criteria.where("name").is(categoryName));
        return mongoTemplate.findOne(query, CategoryDO.class, COLLECTION_NAME);
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param categorySlugName 分类缩略名
     * @return 一个分类信息
     */
    public CategoryDO getCategoryInfoBySlug(String userId, String categorySlugName) {
        Query query = new Query();
        if (!CharSequenceUtil.isBlank(userId)) {
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        } else {
            query.addCriteria(Criteria.where(IUserService.USER_ID).exists(false));
        }
        query.addCriteria(Criteria.where("slug").is(categorySlugName));
        CategoryDO categoryDO = mongoTemplate.findOne(query, CategoryDO.class, COLLECTION_NAME);
        if(categoryDO == null){
            categoryDO = getCategoryInfo(userId, categorySlugName);
        }
        return categoryDO;
    }

    /***
     * 通过Id获取分类信息
     * @param categoryId 分类id
     * @return 一个分类信息
     */
    public CategoryDO getCategoryInfo(String categoryId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryId));
        return mongoTemplate.findOne(query, CategoryDO.class, COLLECTION_NAME);
    }

    /***
     * 添加分类
     * @param categoryDTO 参数对象
     * @return ResponseResult
     */
    public ResponseResult<Object> add(CategoryDTO categoryDTO) {
        if (getCategoryInfo(categoryDTO.getUserId(), categoryDTO.getName()) != null) {
            return ResultUtil.warning("该分类名称已存在");
        }
        if (!CharSequenceUtil.isBlank(categoryDTO.getParentCategoryId())) {
            CategoryDO parentCategoryDO = getCategoryInfo(categoryDTO.getParentCategoryId());
            if (parentCategoryDO == null) {
                return ResultUtil.warning("该父分类不存在");
            }
        }
        categoryDTO.setSlug(getSlug(categoryDTO));
        CategoryDO categoryDO = new CategoryDO();
        BeanUtils.copyProperties(categoryDTO, categoryDO);
        categoryDO.setId(null);
        mongoTemplate.save(categoryDO, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 更新分类
     * @param categoryDTO 参数对象
     * @return ResponseResult
     */
    public ResponseResult<Object> update(CategoryDTO categoryDTO) {
        CategoryDO categoryDO1 = getCategoryInfo(categoryDTO.getId());
        if (categoryDO1 == null) {
            return ResultUtil.warning("该分类不存在");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(categoryDTO.getId()));
        query1.addCriteria(Criteria.where("name").is(categoryDTO.getName()));
        if(mongoTemplate.exists(query1, COLLECTION_NAME)){
            return ResultUtil.warning("该分类名称已存在");
        }
        categoryDTO.setSlug(getSlug(categoryDTO));
        CategoryDO categoryDO = new CategoryDO();
        BeanUtils.copyProperties(categoryDTO, categoryDO);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryDTO.getId()));
        Update update = MongoUtil.getUpdate(categoryDO);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    private String getSlug(CategoryDTO categoryDTO) {
        Query query = new Query();
        String slug = categoryDTO.getSlug();
        if (CharSequenceUtil.isBlank(slug)) {
            return categoryDTO.getName();
        }
        String id = categoryDTO.getId();
        if (id != null) {
            query.addCriteria(Criteria.where("_id").nin(id));
        }
        query.addCriteria(Criteria.where("slug").is(slug));
        if (mongoTemplate.exists(query, COLLECTION_NAME)) {
            return slug + "-1";
        }
        return slug;
    }

    /***
     * 设置默认分类
     * @param categoryId categoryId
     * @return ResponseResult
     */
    public ResponseResult<Object> setDefault(String categoryId) {
        Query query2 = new Query();
        query2.addCriteria(Criteria.where("isDefault").is(true));
        Update update2 = new Update();
        update2.set("isDefault", false);
        mongoTemplate.updateMulti(query2, update2, COLLECTION_NAME);
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").is(categoryId));
        Update update1 = new Update();
        update1.set("isDefault", true);
        mongoTemplate.upsert(query1, update1, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除分类及其子分类下的所有分类及文章
     * @param categoryIdList 分类id列表
     */
    public void delete(List<String> categoryIdList) {
        List<String> categoryIds = findLoopCategory(true, categoryIdList);
        // 删除所有关联的分类
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(categoryIds));
        mongoTemplate.remove(query, COLLECTION_NAME);
        // 删除所有关联的文章
        // Query query1 = new Query();
        // query.addCriteria(Criteria.where("categoryIds").in(categoryIds));
        // Console.log(categoryIds);
        //mongoTemplate.remove(query1, FileServiceImpl.COLLECTION_NAME);
    }

    /***
     * 递归查找分类id及其子分类id列表
     * @param firstCategory 是否是第一次查找
     * @param categoryIdList 分类id列表
     */
    private List<String> findLoopCategory(boolean firstCategory, List<String> categoryIdList) {
        final List<String> categoryIds = new ArrayList<>();
        Query query = new Query();
        if (firstCategory) {
            query.addCriteria(Criteria.where("_id").in(categoryIdList));
        } else {
            query.addCriteria(Criteria.where("parentCategoryId").in(categoryIdList));
            categoryIds.addAll(categoryIdList);
        }
        List<String> categoryIdList1 = mongoTemplate.find(query, CategoryDO.class, COLLECTION_NAME).stream().map(CategoryDO::getId).collect(Collectors.toList());
        if (!categoryIdList1.isEmpty()) {
            categoryIds.addAll(findLoopCategory(false, categoryIdList1));
        }
        return categoryIds;
    }

}
