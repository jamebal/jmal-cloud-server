package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ICategoryDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.CategoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.category.CategoryOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.CategoryDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CategoryDAOJpaImpl implements ICategoryDAO, IWriteCommon<CategoryDO> {

    private final CategoryRepository categoryRepository;

    private final IWriteService writeService;

    @Override
    public void AsyncSaveAll(Iterable<CategoryDO> entities) {
        writeService.submit(new CategoryOperation.CreateAll(entities));
    }

    @Override
    public List<CategoryDO> findCategories(String userId) {
        if (!CharSequenceUtil.isBlank(userId)) {
            return categoryRepository.findByUserId(userId);
        } else {
            return categoryRepository.findByUserIdIsNull();
        }
    }

    @Override
    public List<CategoryDO> findCategoryListByIds(List<String> categoryIds) {
        return categoryRepository.findAllById(categoryIds);
    }

    @Override
    public CategoryDO findCategoryByName(String userId, String categoryName) {
        if (!CharSequenceUtil.isBlank(userId)) {
            return categoryRepository.findByUserIdAndName(userId, categoryName).orElse(null);
        } else {
            return categoryRepository.findByUserIdIsNullAndName(categoryName).orElse(null);
        }
    }

    @Override
    public CategoryDO findCategoryBySlug(String userId, String categorySlugName) {
        if (!CharSequenceUtil.isBlank(userId)) {
            return categoryRepository.findByUserIdAndSlug(userId, categorySlugName).orElse(null);
        } else {
            return categoryRepository.findByUserIdIsNullAndSlug(categorySlugName).orElse(null);
        }
    }

    @Override
    public CategoryDO findById(String categoryId) {
        return categoryRepository.findById(categoryId).orElse(null);
    }

    @Override
    public void save(CategoryDO categoryDO) {
        try {
            writeService.submit(new CategoryOperation.CreateAll(Collections.singleton(categoryDO))).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean existsByNameAndIdIsNot(String id, String name) {
        return categoryRepository.existsByNameAndIdIsNot(name, id);
    }

    @Override
    public void upsert(CategoryDO categoryDO) {
        try {
            writeService.submit(new CategoryOperation.Upsert(categoryDO)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean existsBySlugAndIdIsNot(String slug, String id) {
        if (id == null) {
            return categoryRepository.existsBySlug(slug);
        } else {
            return categoryRepository.existsBySlugAndIdIsNot(slug, id);
        }
    }

    @Override
    public void updateSetDefaultFalseByDefaultIsTrue() {
        try {
            writeService.submit(new CategoryOperation.UpdateSetDefaultFalseByDefaultIsTrue()).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void updateSetDefaultTrueById(String categoryId) {
        try {
            writeService.submit(new CategoryOperation.UpdateSetDefaultTrueById(categoryId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void deleteAllByIdIn(Collection<String> categoryIds) {
        try {
            writeService.submit(new CategoryOperation.DeleteAllByIdIn(categoryIds)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<CategoryDO> findAll() {
        return categoryRepository.findAll();
    }

}
