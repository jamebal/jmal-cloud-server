package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.CategoryDO;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.List;

public interface ICategoryDAO {

    List<CategoryDO> findCategories(String userId);

    List<CategoryDO> findCategoryListByIds(List<String> categoryIds);

    CategoryDO findCategoryByName(String userId, String categoryName);

    CategoryDO findCategoryBySlug(String userId, String categorySlugName);

    CategoryDO findById(String categoryId);

    void save(CategoryDO categoryDO);

    boolean existsByNameAndIdIsNot(@NotNull(message = "分类id不能为空") String id, @NotNull(message = "分类名称不能为空") String name);

    void upsert(CategoryDO categoryDO);

    boolean existsBySlugAndIdNot(String slug, String id);

    void updateSetDefaultFalseByDefaultIsTrue();

    void updateSetDefaultTrueById(String categoryId);

    void deleteAllByIdIn(Collection<String> categoryIds);

    List<CategoryDO> findAll();
}
