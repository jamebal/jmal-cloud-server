package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson2.JSON;
import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import com.jmal.clouddisk.model.rbac.RoleDTO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description 角色管理
 * @blame jmal
 * @Date 2021/1/7 7:45 下午
 */
@Service
@Slf4j
public class RoleService {

    /***
     * 超级管理员, 拥有所有权限
     */
    public static final String ADMINISTRATORS = "Administrators";

    public static final String COLLECTION_NAME = "role";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MenuService menuService;

    @Autowired
    private IUserService userService;

    @Autowired
    private MessageUtil messageUtil;

    /***
     * 角色列表
     * @param queryDTO 角色查询条件
     * @return ResponseResult
     */
    public ResponseResult<List<RoleDTO>> list(QueryRoleDTO queryDTO) {
        Query query = new Query();
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        MongoUtil.commonQuery(queryDTO, query);
        if(!CharSequenceUtil.isBlank(queryDTO.getName())){
            query.addCriteria(Criteria.where("name").regex(queryDTO.getName(), "i"));
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getCode())){
            query.addCriteria(Criteria.where("code").regex(queryDTO.getCode(), "i"));
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getRemarks())){
            query.addCriteria(Criteria.where("remarks").regex(queryDTO.getRemarks(), "i"));
        }
        List<RoleDTO> roleDTOList = mongoTemplate.find(query, RoleDTO.class, COLLECTION_NAME);
        // to i18n
        roleDTOList = roleDTOList.stream().peek(roleDTO -> {
            roleDTO.setName(messageUtil.getMessage(roleDTO.getName()));
            roleDTO.setRemarks(messageUtil.getMessage(roleDTO.getRemarks()));
        }).collect(Collectors.toList());
        return ResultUtil.success(roleDTOList).setCount(count);
    }

    /***
     * roleCode是否存在
     * @param roleCode  角色标识
     * @return 角色标识是否存在
     */
    private boolean existsRoleCode(String roleCode){
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(roleCode));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    /***
     * roleId是否存在
     * @param roleId roleId
     * @return roleId是否存在
     */
    private boolean existsRoleId(String roleId){
        Query query = new Query();
        query.addCriteria(Criteria.where("roleId").is(roleId));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    /***
     * 添加角色
     * @param roleDTO 添加角色
     */
    public ResponseResult<Object> add(RoleDTO roleDTO) {
        if (existsRoleCode(roleDTO.getCode())) {
            return ResultUtil.warning("该角色标识已存在");
        }
        RoleDO roleDO = new RoleDO();
        BeanUtils.copyProperties(roleDTO, roleDO);
        LocalDateTime dateNow = LocalDateTime.now(TimeUntils.ZONE_ID);
        roleDO.setCreateTime(dateNow);
        roleDO.setUpdateTime(dateNow);
        roleDO.setId(null);
        mongoTemplate.save(roleDO, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 更新角色
     * @param roleDTO RoleDTO
     */
    public ResponseResult<Object> update(RoleDTO roleDTO) {
        if (existsRoleId(roleDTO.getId())) {
            return ResultUtil.warning("不存在的角色Id");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(roleDTO.getId()));
        query1.addCriteria(Criteria.where("code").is(roleDTO.getCode()));
        if(mongoTemplate.exists(query1, COLLECTION_NAME)){
            return ResultUtil.warning("该分类标识已存在");
        }
        RoleDO roleDO = new RoleDO();
        BeanUtils.copyProperties(roleDTO, roleDO);
        roleDO.setUpdateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(roleDO.getId()));
        if(roleDO.getMenuIds() != null){
            // 分配权限后更新相关角色用户的权限缓存
            ThreadUtil.execute(() -> updateUserCacheByRole(roleDO));
        }
        Update update = MongoUtil.getUpdate(roleDO);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 更新相关角色的用户缓存
     * @param roleDO RoleDO
     */
    private void updateUserCacheByRole(RoleDO roleDO) {
        if(roleDO.getId() == null){
            return;
        }
        if(roleDO.getMenuIds() == null || roleDO.getMenuIds().isEmpty()){
            return;
        }
        // 根据角色获取用户名列表
        List<String> usernameList = userService.getUserNameListByRole(roleDO.getId());
        updateUserCacheByNames(usernameList);
    }

    /***
     * 更新相关角色的用户缓存
     * @param usernameList 用户名列表
     */
    private void updateUserCacheByNames(List<String> usernameList) {
        usernameList.forEach(username -> {
            // 获取该用户最新的权限列表
            List<String> authorities = userService.getAuthorities(username);
            if(CaffeineUtil.existsAuthoritiesCache(username)){
                CaffeineUtil.setAuthoritiesCache(username, authorities);
            }
        });
    }

    /***
     * 更新相关角色的用户缓存
     * @param rolesIds rolesIds
     */
    public void updateUserCacheByRole(List<String> rolesIds) {
        List<String> usernameList = userService.getUserNameListByRole(rolesIds);
        updateUserCacheByNames(usernameList);
    }

    /***
     * 删除角色
     * @param roleIds 角色id列表
     */
    public void delete(String[] roleIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in((Object[]) roleIds));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    /***
     * 获取角色的菜单id列表
     * @param roleId 角色id
     * @return 菜单id列表
     */
    public List<String> getMenuIdList(String roleId) {
        List<String> menuIdList = new ArrayList<>();
        RoleDO roleDO = mongoTemplate.findById(roleId, RoleDO.class, COLLECTION_NAME);
        if(roleDO != null && roleDO.getMenuIds() != null && !roleDO.getMenuIds().isEmpty()){
            menuIdList = roleDO.getMenuIds();
        }
        return menuIdList;
    }

    /***
     * 根据角色id列表获取菜单id列表
     * @param roleIdList 角色id列表
     * @return 菜单id列表
     */
    public List<String> getMenuIdList(List<String> roleIdList) {
        List<String> menuIdList = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIdList));
        List<RoleDO> roleDOList = mongoTemplate.find(query, RoleDO.class, COLLECTION_NAME);
        List<String> finalMenuIdList = menuIdList;
        roleDOList.forEach(roleDO -> finalMenuIdList.addAll(roleDO.getMenuIds()));
        // 去重
        menuIdList = menuIdList.stream().distinct().collect(Collectors.toList());
        return menuIdList;
    }

    /***
     * 获取权限列表
     * @param roleIdList 角色Id列表
     * @return 权限列表
     */
    public List<String> getAuthorities(List<String> roleIdList) {
        List<String> authorities = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIdList));
        List<RoleDO> roleDOList = mongoTemplate.find(query, RoleDO.class, COLLECTION_NAME);
        if(roleDOList.isEmpty()){
            return authorities;
        }
        List<String> menuIdList = new ArrayList<>();
        for (RoleDO roleDO : roleDOList) {
            // 如果是超级管理员, 直接返回所有权限
            if(ADMINISTRATORS.equals(roleDO.getCode())){
                return AnnoManageUtil.AUTHORITIES;
            }
        }
        roleDOList.forEach(roleDO -> {
            if(roleDO.getMenuIds() != null && !roleDO.getMenuIds().isEmpty()){
                menuIdList.addAll(roleDO.getMenuIds());
            }
        });
        if(menuIdList.isEmpty()){
           return authorities;
        }
        return menuService.getAuthorities(menuIdList);
    }

    /***
     * 获取角色列表
     * @param roleIds 角色id列表
     */
    public List<RoleDTO> getRoleList(List<String> roleIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(roleIds));
        return mongoTemplate.find(query, RoleDTO.class, COLLECTION_NAME);
    }

    /***
     * 获取所有角色
     * @return List<RoleDO>
     */
    public List<RoleDO> getAllRoles() {
        return mongoTemplate.findAll(RoleDO.class, COLLECTION_NAME);
    }

    /***
     * 初始化角色数据
     */
    public void initRoles() {

        TimeInterval timeInterval = new TimeInterval();
        List<RoleDO> roleDOList = getRoleDOListByConfigJSON();
        if (roleDOList.isEmpty()) return;
        roleDOList.parallelStream().forEach(roleDO -> {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(roleDO.getId()));
            boolean exists = mongoTemplate.exists(query, COLLECTION_NAME);
            if (!exists) {
                mongoTemplate.insert(roleDO);
            }
        });
        log.info("更新角色， 耗时:{}ms", timeInterval.intervalMs());
    }

    private static List<RoleDO> getRoleDOListByConfigJSON() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("db/role.json");
        if(inputStream == null){
            return Collections.emptyList();
        }
        String json = new String(IoUtil.readBytes(inputStream), StandardCharsets.UTF_8);
        return JSON.parseArray(json, RoleDO.class);
    }

    /***
     * 获取roleId
     * @param roleCode roleCode
     */
    public String getRoleIdByCode(String roleCode) {
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(roleCode));
        RoleDO roleDO = mongoTemplate.findOne(query, RoleDO.class, COLLECTION_NAME);
        return roleDO == null ? "" : roleDO.getId();
    }

}
