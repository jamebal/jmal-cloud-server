
package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ICategoryDAO;
import com.jmal.clouddisk.model.CategoryDO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.MongoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class CategoryDAOImpl implements ICategoryDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<CategoryDO> findCategories(String userId) {
        Query query = getQuery(userId);
        return mongoTemplate.find(query, CategoryDO.class);
    }

    @Override
    public List<CategoryDO> findCategoryListByIds(List<String> categoryIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(List.of(categoryIds)));
        return mongoTemplate.find(query, CategoryDO.class);
    }

    @Override
    public CategoryDO findCategoryByName(String userId, String categoryName) {
        Query query = getQuery(userId);
        query.addCriteria(Criteria.where("name").is(categoryName));
        return mongoTemplate.findOne(query, CategoryDO.class);
    }

    @Override
    public CategoryDO findCategoryBySlug(String userId, String categorySlugName) {
        Query query = new Query();
        if (!CharSequenceUtil.isBlank(userId)) {
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        } else {
            query.addCriteria(Criteria.where(IUserService.USER_ID).exists(false));
        }
        query.addCriteria(Criteria.where("slug").is(categorySlugName));
        return mongoTemplate.findOne(query, CategoryDO.class);
    }

    @Override
    public CategoryDO findById(String categoryId) {
        return mongoTemplate.findById(categoryId, CategoryDO.class);
    }

    @Override
    public void save(CategoryDO categoryDO) {
        mongoTemplate.save(categoryDO);
    }

    @Override
    public boolean existsByNameAndIdIsNot(String id, String name) {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(name));
        query.addCriteria(Criteria.where("_id").ne(id));
        return mongoTemplate.exists(query, CategoryDO.class);
    }

    @Override
    public void upsert(CategoryDO categoryDO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(categoryDO.getId()));
        Update update = MongoUtil.getUpdate(categoryDO);
        mongoTemplate.upsert(query, update, CategoryDO.class);
    }

    @Override
    public boolean existsBySlugAndIdIsNot(String slug, String id) {
        Query query = new Query();
        if (id != null) {
            query.addCriteria(Criteria.where("_id").nin(id));
        }
        query.addCriteria(Criteria.where("slug").is(slug));
        return mongoTemplate.exists(query, CategoryDO.class);
    }

    @Override
    public void updateSetDefaultFalseByDefaultIsTrue() {
        Query query2 = new Query();
        query2.addCriteria(Criteria.where("isDefault").is(true));
        Update update2 = new Update();
        update2.set("isDefault", false);
        mongoTemplate.updateMulti(query2, update2, CategoryDO.class);
    }

    @Override
    public void updateSetDefaultTrueById(String categoryId) {
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").is(categoryId));
        Update update1 = new Update();
        update1.set("isDefault", true);
        mongoTemplate.upsert(query1, update1, CategoryDO.class);
    }

    @Override
    public void deleteAllByIdIn(Collection<String> categoryIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(categoryIds));
        mongoTemplate.remove(query, CategoryDO.class);
    }

    @Override
    public List<CategoryDO> findAll() {
        return mongoTemplate.findAll(CategoryDO.class);
    }

    private static Query getQuery(String userId) {
        Query query = new Query();
        if(!CharSequenceUtil.isBlank(userId)){
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        } else {
            query.addCriteria(Criteria.where(IUserService.USER_ID).exists(false));
        }
        return query;
    }
}
