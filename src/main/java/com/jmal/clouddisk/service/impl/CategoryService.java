package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IArticleDAO;
import com.jmal.clouddisk.dao.ICategoryDAO;
import com.jmal.clouddisk.model.CategoryDO;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:51 下午
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final IArticleDAO articleDAO;

    private final ICategoryDAO categoryDAO;

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
        long count = articleDAO.countByCategoryIdsAndRelease(categoryDTO.getId());
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
        List<CategoryDO> categoryDOList = categoryDAO.findCategories(userId);
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
    public List<CategoryDO> getCategoryListByIds(String[] categoryIds) {
        return categoryDAO.findCategoryListByIds(List.of(categoryIds));
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param categoryName 分类名称
     * @return 一个分类信息
     */
    public CategoryDO getCategoryInfo(String userId, String categoryName) {
        return categoryDAO.findCategoryByName(userId, categoryName);
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param categorySlugName 分类缩略名
     * @return 一个分类信息
     */
    public CategoryDO getCategoryInfoBySlug(String userId, String categorySlugName) {
        CategoryDO categoryDO = categoryDAO.findCategoryBySlug(userId, categorySlugName);
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
        return categoryDAO.findById(categoryId);
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
        categoryDAO.save(categoryDO);
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
        if(categoryDAO.existsByNameAndIdIsNot(categoryDTO.getId(), categoryDTO.getName())){
            return ResultUtil.warning("该分类名称已存在");
        }
        categoryDTO.setSlug(getSlug(categoryDTO));
        CategoryDO categoryDO = new CategoryDO();
        BeanUtils.copyProperties(categoryDTO, categoryDO);
        categoryDAO.upsert(categoryDO);
        return ResultUtil.success();
    }

    private String getSlug(CategoryDTO categoryDTO) {
        String slug = categoryDTO.getSlug();
        if (CharSequenceUtil.isBlank(slug)) {
            return categoryDTO.getName();
        }
        if (categoryDAO.existsBySlugAndIdIsNot(slug, categoryDTO.getId())) {
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
        categoryDAO.updateSetDefaultFalseByDefaultIsTrue();
        categoryDAO.updateSetDefaultTrueById(categoryId);
        return ResultUtil.success();
    }

    /***
     * 删除分类及其子分类下的所有分类及文章
     * @param categoryIdList 分类id列表
     */
    public void delete(List<String> categoryIdList) {
        Set<String> allIds = findAllSubCategoryIds(categoryIdList);
        // 删除所有关联的分类
        categoryDAO.deleteAllByIdIn(allIds);
    }

    private Set<String> findAllSubCategoryIds(List<String> initialCategoryIds) {
        if (initialCategoryIds == null || initialCategoryIds.isEmpty()) {
            return new HashSet<>();
        }

        // 1. 一次性查询出所有分类，构建父子关系映射
        List<CategoryDO> allCategories = categoryDAO.findAll();
        Map<String, List<String>> parentToChildrenMap = allCategories.stream()
                .filter(c -> c.getParentCategoryId() != null)
                .collect(Collectors.groupingBy(
                        CategoryDO::getParentCategoryId,
                        Collectors.mapping(CategoryDO::getId, Collectors.toList())
                ));

        // 2. 使用队列(BFS)或栈(DFS)进行遍历，不再访问数据库
        Set<String> resultSet = new HashSet<>(initialCategoryIds);
        Queue<String> queue = new LinkedList<>(initialCategoryIds);

        while (!queue.isEmpty()) {
            String parentId = queue.poll();
            List<String> childrenIds = parentToChildrenMap.getOrDefault(parentId, Collections.emptyList());
            for (String childId : childrenIds) {
                if (resultSet.add(childId)) { // 避免循环引用导致的死循环
                    queue.add(childId);
                }
            }
        }

        return new HashSet<>(resultSet);
    }

}
