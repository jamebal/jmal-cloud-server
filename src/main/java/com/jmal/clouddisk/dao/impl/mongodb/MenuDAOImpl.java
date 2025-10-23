package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IMenuDAO;
import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class MenuDAOImpl implements IMenuDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<MenuDO> treeMenu(QueryMenuDTO queryDTO) {
        Query query = new Query();
        if(!CharSequenceUtil.isBlank(queryDTO.getName())){
            query.addCriteria(Criteria.where("name").regex(queryDTO.getName(), "i"));
        }
        List<String> menuIdList = null;
        if(!CharSequenceUtil.isBlank(queryDTO.getUserId())){
            menuIdList = getMenuIdListByUserId(queryDTO.getUserId());
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getRoleId())) {
            if(menuIdList != null){
                menuIdList.addAll(getMenuIdListByRoleId(queryDTO.getRoleId()));
            } else {
                menuIdList = getMenuIdListByRoleId(queryDTO.getRoleId());
            }
        }
        if(menuIdList != null){
            query.addCriteria(Criteria.where("_id").in(menuIdList));
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getPath())){
            query.addCriteria(Criteria.where("path").regex(queryDTO.getPath(), "i"));
            query.addCriteria(Criteria.where("component").regex(queryDTO.getPath(), "i"));
        }
        return mongoTemplate.find(query, MenuDO.class);
    }

    @Override
    public MenuDO findById(String menuId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(menuId));
        return mongoTemplate.findOne(query, MenuDO.class);
    }

    @Override
    public boolean existsByName(String name) {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(name));
        return mongoTemplate.exists(query, MenuDO.class);
    }

    @Override
    public void save(MenuDO menuDO) {
        mongoTemplate.save(menuDO);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(name).and("_id").ne(id));
        return mongoTemplate.exists(query, MenuDO.class);
    }

    @Override
    public List<String> findIdsByParentIdIn(List<String> ids) {
        List<String> menuIdList = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("parentId").in(ids));
        query.fields().include("_id");
        List<MenuDO> menuDOList = mongoTemplate.find(query, MenuDO.class);
        if(!menuDOList.isEmpty()){
            menuIdList = menuDOList.stream().map(MenuDO::getId).collect(Collectors.toList());
        }
        return menuIdList;
    }

    @Override
    public void removeByIdIn(Collection<String> idList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(idList));
        mongoTemplate.remove(query, MenuDO.class);
    }

    @Override
    public List<String> findAuthorityAllByIds(List<String> menuIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(menuIdList));
        query.fields().include("authority");
        List<String> authorities = new ArrayList<>();
        List<MenuDO> menuDOList = mongoTemplate.find(query, MenuDO.class);

        menuDOList.forEach(menuDO -> {
            if(!CharSequenceUtil.isBlank(menuDO.getAuthority())){
                authorities.add(menuDO.getAuthority());
            }
        });
        return authorities;
    }

    @Override
    public boolean existsById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        return mongoTemplate.exists(query, MenuDO.class);
    }

    @Override
    public boolean exists() {
        return mongoTemplate.exists(new Query(), MenuDO.class);
    }

    @Override
    public void saveAll(List<MenuDO> menuDOList) {
        mongoTemplate.insertAll(menuDOList);
    }

    /**
     * 获取角色的菜单id列表
     * @param roleId 角色id
     * @return 菜单id列表
     */
    public List<String> getMenuIdListByRoleId(String roleId) {
        List<String> menuIdList = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_Id").is(roleId));
        query.fields().include("menuIds");
        RoleDO roleDO = mongoTemplate.findOne(query, RoleDO.class);
        if(roleDO != null && roleDO.getMenuIds() != null && !roleDO.getMenuIds().isEmpty()){
            menuIdList = roleDO.getMenuIds();
        }
        return menuIdList;
    }

    public List<String> getMenuIdListByUserId(String userId) {
        List<String> menuIdList = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(userId));
        query.fields().include("roles").include("creator");
        ConsumerDO consumerDO = mongoTemplate.findOne(query, ConsumerDO.class);
        if (consumerDO == null || consumerDO.getRoles() == null) {
            return menuIdList;
        }
        if (consumerDO.getCreator() != null && consumerDO.getCreator()) {
            // 如果是创建者则返回所有菜单
            return getAllMenuIdList();
        }
        return getMenuIdListByRoleIdList(consumerDO.getRoles());
    }

    /***
     * 根据角色id列表获取菜单id列表
     * @param roleIdList 角色id列表
     * @return 菜单id列表
     */
    public List<String> getMenuIdListByRoleIdList(List<String> roleIdList) {
        List<String> menuIdList = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIdList));
        query.fields().include("menuIds");
        List<RoleDO> roleDOList = mongoTemplate.find(query, RoleDO.class);
        List<String> finalMenuIdList = menuIdList;
        roleDOList.forEach(roleDO -> finalMenuIdList.addAll(roleDO.getMenuIds()));
        // 去重
        menuIdList = menuIdList.stream().distinct().collect(Collectors.toList());
        return menuIdList;
    }

    public List<MenuDO> getAllMenus() {
        return mongoTemplate.findAll(MenuDO.class);
    }

    /**
     * 获取所有菜单Id
     * @return List<String>
     */
    public List<String> getAllMenuIdList() {
        List<MenuDO> menuDOList = getAllMenus();
        return menuDOList.stream().map(MenuDO::getId).toList();
    }
}
