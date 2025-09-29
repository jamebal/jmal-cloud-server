package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.IRoleDAO;
import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import com.jmal.clouddisk.model.rbac.RoleDTO;
import com.jmal.clouddisk.util.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleService {

    /***
     * 超级管理员, 拥有所有权限
     */
    public static final String ADMINISTRATORS = "Administrators";

    public static final String COLLECTION_NAME = "role";

    public static final String ROLES = "roles";

    private final IRoleDAO roleDAO;

    private final IUserDAO userDAO;

    private final DataSourceProperties dataSourceProperties;

    private final CommonUserService commonUserService;

    private final MenuService menuService;

    private final MessageUtil messageUtil;

    /***
     * 角色列表
     * @param queryDTO 角色查询条件
     * @return ResponseResult
     */
    public ResponseResult<List<RoleDTO>> list(QueryRoleDTO queryDTO) {
        Page<RoleDO> roleDOPage = roleDAO.page(queryDTO);
        // to i18n
        List<RoleDTO> roleDTOList = roleDOPage.getContent().stream().map(this::getRoleDTO).collect(Collectors.toList());
        return ResultUtil.success(roleDTOList).setCount(roleDOPage.getTotalElements());
    }

    /***
     * roleCode是否存在
     * @param roleCode  角色标识
     * @return 角色标识是否存在
     */
    private boolean existsRoleCode(String roleCode) {
        return roleDAO.existsByCode(roleCode);
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
        roleDAO.save(roleDO);
        return ResultUtil.success();
    }

    /***
     * 更新角色
     * @param roleDTO RoleDTO
     */
    public ResponseResult<Object> update(RoleDTO roleDTO) {
        if (!roleDAO.existsById(roleDTO.getId())) {
            return ResultUtil.warning("不存在的角色Id");
        }
        if (roleDAO.existsByCodeAndIdNot(roleDTO.getCode(), roleDTO.getId())) {
            return ResultUtil.warning("该角色标识已存在");
        }
        RoleDO roleDO = new RoleDO();
        BeanUtils.copyProperties(roleDTO, roleDO);
        roleDO.setUpdateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
        if (roleDO.getMenuIds() != null) {
            // 分配权限后更新相关角色用户的权限缓存
            Completable.fromAction(() -> updateUserCacheByRole(roleDO))
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        }
        roleDAO.save(roleDO);
        return ResultUtil.success();
    }

    /***
     * 更新相关角色的用户缓存
     * @param roleDO RoleDO
     */
    private void updateUserCacheByRole(RoleDO roleDO) {
        if (roleDO.getId() == null) {
            return;
        }
        if (roleDO.getMenuIds() == null || roleDO.getMenuIds().isEmpty()) {
            return;
        }
        // 根据角色获取用户名列表
        List<String> usernameList = userDAO.findUsernamesByRoleIdList(Collections.singleton(roleDO.getId()));
        updateUserCacheByNames(usernameList);
    }

    /***
     * 更新相关角色的用户缓存
     * @param usernameList 用户名列表
     */
    private void updateUserCacheByNames(List<String> usernameList) {
        usernameList.forEach(username -> {
            // 获取该用户最新的权限列表
            List<String> authorities = getAuthorities(username);
            if (CaffeineUtil.existsAuthoritiesCache(username)) {
                CaffeineUtil.setAuthoritiesCache(username, authorities);
            }
        });
    }

    /***
     * 更新相关角色的用户缓存
     * @param rolesIds rolesIds
     */
    public void updateUserCacheByRole(List<String> rolesIds) {
        List<String> usernameList = userDAO.findUsernamesByRoleIdList(rolesIds);
        updateUserCacheByNames(usernameList);
    }

    /***
     * 删除角色
     * @param roleIds 角色id列表
     */
    public void delete(String[] roleIds) {
        roleDAO.removeByIdIn(List.of(roleIds));
    }

    public List<String> getAuthorities(String username) {
        List<String> authorities = new ArrayList<>();
        ConsumerDO consumerDO = commonUserService.getUserInfoByUsername(username);
        if (consumerDO == null) {
            return authorities;
        }
        // 如果是创建者, 直接返回所有权限
        if (consumerDO.getCreator() != null && consumerDO.getCreator()) {
            return AnnoManageUtil.AUTHORITIES;
        }
        List<String> roleIdList = consumerDO.getRoles();
        if (roleIdList == null || roleIdList.isEmpty()) {
            return authorities;
        }
        return getAuthorities(roleIdList);
    }

    /***
     * 获取权限列表
     * @param roleIdList 角色Id列表
     * @return 权限列表
     */
    public List<String> getAuthorities(List<String> roleIdList) {
        List<String> authorities = new ArrayList<>();
        List<RoleDO> roleDOList = roleDAO.findAllByIdIn(roleIdList);
        if (roleDOList.isEmpty()) {
            return authorities;
        }
        List<String> menuIdList = new ArrayList<>();
        for (RoleDO roleDO : roleDOList) {
            // 如果是超级管理员, 直接返回所有权限
            if (ADMINISTRATORS.equals(roleDO.getCode())) {
                return AnnoManageUtil.AUTHORITIES;
            }
        }
        roleDOList.forEach(roleDO -> {
            if (roleDO.getMenuIds() != null && !roleDO.getMenuIds().isEmpty()) {
                menuIdList.addAll(roleDO.getMenuIds());
            }
        });
        if (menuIdList.isEmpty()) {
            return authorities;
        }
        return menuService.getAuthorities(menuIdList);
    }

    /***
     * 获取角色列表
     * @param roleIds 角色id列表
     */
    public List<RoleDTO> getRoleList(List<String> roleIds) {
        List<RoleDO> roleDOList = roleDAO.findAllByIdIn(roleIds);
        if (roleDOList.isEmpty()) {
            return List.of();
        }
        return roleDOList.stream().map(this::getRoleDTO).collect(Collectors.toList());
    }

    private RoleDTO getRoleDTO(RoleDO roleDO) {
        RoleDTO roleDTO = new RoleDTO();
        BeanUtils.copyProperties(roleDO, roleDTO);
        roleDTO.setName(messageUtil.getMessage(roleDO.getName()));
        roleDTO.setRemarks(messageUtil.getMessage(roleDO.getRemarks()));
        return roleDTO;
    }

    /***
     * 初始化角色数据
     */
    public void initRoles() {
        TimeInterval timeInterval = new TimeInterval();
        List<RoleDO> roleDOList = getRoleDOListByConfigJSON();
        if (roleDOList.isEmpty()) return;
        List<RoleDO> needUpdateRoleList = new ArrayList<>();
        roleDOList.forEach(roleDO -> {
            if (!roleDAO.existsById(roleDO.getId())) {
                needUpdateRoleList.add(roleDO);
            }
        });
        if (needUpdateRoleList.isEmpty()) return;
        roleDAO.saveAll(needUpdateRoleList);
        log.info("更新角色， 耗时:{}ms", timeInterval.intervalMs());
    }

    private static List<RoleDO> getRoleDOListByConfigJSON() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("db/role.json");
        if (inputStream == null) {
            return Collections.emptyList();
        }
        String json = new String(IoUtil.readBytes(inputStream), StandardCharsets.UTF_8);
        return JacksonUtil.parseArray(json, RoleDO.class);
    }

    /***
     * 获取roleId
     * @param roleCode roleCode
     */
    public String getRoleIdByCode(String roleCode) {
        RoleDO roleDO = roleDAO.findByCode(roleCode);
        return roleDO == null ? "" : roleDO.getId();
    }

}
