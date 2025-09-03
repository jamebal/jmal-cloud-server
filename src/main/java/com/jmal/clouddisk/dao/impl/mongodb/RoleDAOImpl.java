package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IRoleDAO;
import com.jmal.clouddisk.dao.util.PageableUtil;
import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class RoleDAOImpl implements IRoleDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<RoleDO> page(QueryRoleDTO queryDTO) {

        Query query = new Query();
        if(!CharSequenceUtil.isBlank(queryDTO.getName())){
            query.addCriteria(Criteria.where("name").regex(queryDTO.getName(), "i"));
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getCode())){
            query.addCriteria(Criteria.where("code").regex(queryDTO.getCode(), "i"));
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getRemarks())){
            query.addCriteria(Criteria.where("remarks").regex(queryDTO.getRemarks(), "i"));
        }
        long total = mongoTemplate.count(query, RoleDO.class);

        Pageable pageable = PageableUtil.buildPageable(queryDTO);
        if (total == 0) {
            return Page.empty(pageable);
        }
        query.with(pageable);

        List<RoleDO> roleDOList = mongoTemplate.find(query, RoleDO.class);

        return  new PageImpl<>(roleDOList, pageable, total);

    }

    @Override
    public boolean existsByCode(String roleCode) {
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(roleCode));
        return mongoTemplate.exists(query, RoleDO.class);
    }

    @Override
    public boolean existsById(String roleId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(roleId));
        return mongoTemplate.exists(query, RoleDO.class);
    }

    @Override
    public void save(RoleDO roleDO) {
        mongoTemplate.save(roleDO);
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").nin(id));
        query.addCriteria(Criteria.where("code").is(code));
        return mongoTemplate.exists(query, RoleDO.class);
    }

    @Override
    public void removeByIdIn(List<String> roleIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIdList));
        mongoTemplate.remove(query, RoleDO.class);
    }

    @Override
    public List<RoleDO> findAllByIdIn(List<String> roleIdList) {
        if(roleIdList == null || roleIdList.isEmpty()){
            return List.of();
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIdList));
        return mongoTemplate.find(query, RoleDO.class);
    }

    @Override
    public RoleDO findByCode(String roleCode) {
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(roleCode));
        return mongoTemplate.findOne(query, RoleDO.class);
    }
}
