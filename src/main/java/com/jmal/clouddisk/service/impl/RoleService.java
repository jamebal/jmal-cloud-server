package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.dao.IGroupDAO;
import com.jmal.clouddisk.dao.IRoleDAO;
import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.GroupDO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import com.jmal.clouddisk.model.rbac.RoleDTO;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.JacksonUtil;
import com.jmal.clouddisk.util.MessageUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private final IGroupDAO groupDAO;

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
        roleDAO.save(roleDO);
        // 分配权限后更新相关角色用户的权限缓存
        updateUserCacheByRole(roleDO);
        return ResultUtil.success();
    }

    /**
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
        updateUserCacheByRoleIds(Collections.singleton(roleDO.getId()));
    }

    /**
     * 更新相关角色的用户缓存
     * @param roleIds 角色id列表
     */
    private void updateUserCacheByRoleIds(Collection<String> roleIds) {
        // 获取拥有这些角色的用户列表
        List<String> usernameList = userDAO.findUsernamesByRoleIdList(roleIds);
        Set<String> usernameSet = new HashSet<>(usernameList);
        // 获取拥有这些角色的用户组列表
        List<GroupDO> groupDOList = groupDAO.findAllByRoleIdList(roleIds);
        List<String> groupIds = groupDOList.stream().map(GroupDO::getId).collect(Collectors.toList());
        usernameSet.addAll(userDAO.findUsernamesByGroupIdList(groupIds));
        // 刷新指定用户的权限缓存
        usernameSet.forEach(this::refreshUserAuthoritiesCache);
    }

    /***
     * 删除角色
     * @param roleIds 角色id列表
     */
    public void delete(String[] roleIds) {
        List<String> roleIdList = List.of(roleIds);
        roleDAO.removeByIdIn(roleIdList);

        List<String> usernameList = userDAO.findUsernamesByRoleIdList(roleIdList);
        Set<String> usernameSet = new HashSet<>(usernameList);

        // 清除用户组角色关联
        Set<GroupDO> updateGroups = new HashSet<>();
        List<GroupDO> groupDOList = groupDAO.findAllByRoleIdList(roleIdList);
        List<String> groupIds = groupDOList.stream().map(GroupDO::getId).collect(Collectors.toList());
        usernameSet.addAll(userDAO.findUsernamesByGroupIdList(groupIds));
        for (GroupDO groupDO : groupDOList) {
            if (groupDO.getRoles() != null) {
                groupDO.getRoles().removeAll(roleIdList);
                updateGroups.add(groupDO);
            }
        }
        // 清除用户角色关联
        Set<ConsumerDO> updatedUsers = new HashSet<>();
        if (!usernameList.isEmpty()) {
            List<ConsumerDO> users = userDAO.findAllByUsername(usernameList);
            for (ConsumerDO user : users) {
                if (user.getRoles() != null) {
                    user.getRoles().removeAll(roleIdList);
                    updatedUsers.add(user);
                }
            }
        }

        userDAO.saveAll(updatedUsers);

        // 更新用户缓存
        updatedUsers.forEach(consumerDO -> CaffeineUtil.setConsumerByUsernameCache(consumerDO.getUsername(), consumerDO));

        groupDAO.saveAll(updateGroups);

        // 刷新指定用户的权限缓存
        usernameSet.forEach(this::refreshUserAuthoritiesCache);
    }

    /**
     * 获取用户的最终权限列表（并集）
     * @param username 用户名
     * @return 权限标识列表 (如: ["sys:user:add", "sys:file:list"])
     */
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

        // 收集所有角色ID
        Set<String> allRoleIds = getAllRoleIds(consumerDO);

        if (allRoleIds.isEmpty()) {
            return authorities;
        }

        return getAuthorities(new ArrayList<>(allRoleIds));
    }

    @NotNull
    private Set<String> getAllRoleIds(ConsumerDO consumerDO) {
        Set<String> allRoleIds = new HashSet<>();

        // 添加用户直属的角色
        if (consumerDO.getRoles() != null) {
            allRoleIds.addAll(consumerDO.getRoles());
        }

        // 添加用户所属组的角色
        if (consumerDO.getGroups() != null && !consumerDO.getGroups().isEmpty()) {
            // 查询用户所属的所有组
            List<GroupDO> groups = groupDAO.findAllByIdIn(consumerDO.getGroups());
            for (GroupDO group : groups) {
                if (group.getRoles() != null) {
                    allRoleIds.addAll(group.getRoles());
                }
            }
        }
        return allRoleIds;
    }

    /**
     * 刷新指定用户的权限缓存
     */
    public void refreshUserAuthoritiesCache(String username) {
        List<String> authorities = getAuthorities(username);
        CaffeineUtil.setAuthoritiesCache(username, authorities);
    }

    /**
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

    private RoleDTO getRoleDTO(RoleDO roleDO, String... ignoreProperties) {
        RoleDTO roleDTO = new RoleDTO();
        BeanUtils.copyProperties(roleDO, roleDTO, ignoreProperties);
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

    private boolean isAdministratorsByRoleIds(Set<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        List<String> listCode = roleDAO.findAllCodeByIdIn(roleIds);
        return !listCode.isEmpty() && listCode.contains(ADMINISTRATORS);
    }

    public boolean isAdministratorsByUserId(String userId) {
        ConsumerDO consumerDO = commonUserService.getUserInfoById(userId);
        if (consumerDO == null) {
            return false;
        }
        // 检查是否为创建者
        if (Boolean.TRUE.equals(consumerDO.getCreator())) {
            return true;
        }

        Set<String> allRoleIds = getAllRoleIds(consumerDO);

        // 检查是否为管理员角色
        return isAdministratorsByRoleIds(allRoleIds);
    }

    public List<RoleDO> getAllRoles() {
        QueryRoleDTO queryRoleDTO = new QueryRoleDTO();
        queryRoleDTO.setPageSize(Integer.MAX_VALUE);
        Page<RoleDO> roleDOPage = roleDAO.page(queryRoleDTO);
        return roleDOPage.getContent();
    }
}
