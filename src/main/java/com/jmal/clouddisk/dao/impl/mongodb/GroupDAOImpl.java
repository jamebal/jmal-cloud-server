package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IGroupDAO;
import com.jmal.clouddisk.dao.util.PageableUtil;
import com.jmal.clouddisk.model.query.QueryGroupDTO;
import com.jmal.clouddisk.model.rbac.GroupDO;
import com.jmal.clouddisk.service.impl.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class GroupDAOImpl implements IGroupDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<GroupDO> page(QueryGroupDTO queryGroupDTO) {
        Query query = new Query();
        if (!CharSequenceUtil.isBlank(queryGroupDTO.getName())) {
            query.addCriteria(Criteria.where("name").regex(queryGroupDTO.getName(), "i"));
        }
        if (!CharSequenceUtil.isBlank(queryGroupDTO.getCode())) {
            query.addCriteria(Criteria.where("code").regex(queryGroupDTO.getCode(), "i"));
        }

        long total = mongoTemplate.count(query, GroupDO.class);

        Pageable pageable = PageableUtil.buildPageable(queryGroupDTO);
        if (total == 0) {
            return Page.empty(pageable);
        }
        query.with(pageable);

        List<GroupDO> groupDOList = mongoTemplate.find(query, GroupDO.class);

        return new PageImpl<>(groupDOList, pageable, total);
    }

    @Override
    public boolean existsByCode(String groupCode) {
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(groupCode));
        return mongoTemplate.exists(query, GroupDO.class);
    }

    @Override
    public void save(GroupDO groupDO) {
        mongoTemplate.save(groupDO);
    }

    @Override
    public void saveAll(Set<GroupDO> updateGroups) {
        updateGroups.forEach(mongoTemplate::save);
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").nin(id));
        query.addCriteria(Criteria.where("code").is(code));
        return mongoTemplate.exists(query, GroupDO.class);
    }

    @Override
    public void removeByIdIn(List<String> groupIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(groupIds));
        mongoTemplate.remove(query, GroupDO.class);
    }

    @Override
    public List<GroupDO> findAllByIdIn(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(groupIds));
        return mongoTemplate.find(query, GroupDO.class);
    }

    @Override
    public Optional<GroupDO> findById(String id) {
        GroupDO groupDO = mongoTemplate.findById(id, GroupDO.class);
        return Optional.ofNullable(groupDO);
    }

    @Override
    public List<GroupDO> findAllByRoleIdList(Collection<String> roleIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(RoleService.ROLES).in(roleIdList));
        return mongoTemplate.find(query, GroupDO.class);
    }
}
