package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IGroupDAO;
import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.model.query.QueryGroupDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.GroupAssignDTO;
import com.jmal.clouddisk.model.rbac.GroupDO;
import com.jmal.clouddisk.model.rbac.GroupDTO;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
public class GroupService {

    public static final String COLLECTION_NAME = "user_groups";

    private final IGroupDAO groupDAO;

    private final IUserDAO userDAO;

    private final RoleService roleService;

    /**
     * 组列表
     * @param queryDTO 组查询条件
     * @return ResponseResult
     */
    public ResponseResult<List<GroupDTO>> list(QueryGroupDTO queryDTO) {
        Page<GroupDO> groupDTOPage = groupDAO.page(queryDTO);
        // 转换为DTO
        List<GroupDTO> groupDTOList = groupDTOPage.map(this::convertToDTO).getContent();
        return ResultUtil.success(groupDTOList).setCount(groupDTOPage.getTotalElements());
    }

    /**
     * 添加用户组
     */
    public ResponseResult<Object> add(GroupDTO groupDTO) {
        if (groupDAO.existsByCode(groupDTO.getCode())) {
            return ResultUtil.warning("该组标识已存在");
        }
        GroupDO groupDO = new GroupDO();
        BeanUtils.copyProperties(groupDTO, groupDO);

        Instant now = Instant.now();
        groupDO.setCreatedTime(now);
        groupDO.setUpdatedTime(now);
        groupDO.setId(null);

        groupDAO.save(groupDO);
        return ResultUtil.success();
    }

    /**
     * 更新用户组
     */
    public ResponseResult<Object> update(GroupDTO groupDTO) {
        if (CharSequenceUtil.isBlank(groupDTO.getId())) {
            return ResultUtil.warning("ID不能为空");
        }
        // 检查Code是否与其他组冲突
        if (groupDAO.existsByCodeAndIdNot(groupDTO.getCode(), groupDTO.getId())) {
            return ResultUtil.warning("该组标识已存在");
        }

        GroupDO groupDO = groupDAO.findById(groupDTO.getId()).orElse(null);
        if (groupDO == null) {
            return ResultUtil.warning("用户组不存在");
        }

        // 复制属性
        BeanUtils.copyProperties(groupDTO, groupDO, "id", "createdTime");
        groupDO.setUpdatedTime(Instant.now());

        groupDAO.save(groupDO);

        // 异步刷新属于该组的所有用户的权限缓存
        Completable.fromAction(() -> refreshUserAuthoritiesCacheByGroupIds(Collections.singleton(groupDO.getId())))
                .subscribeOn(Schedulers.io())
                .doOnError(e -> log.error("刷新用户组缓存失败: {}", e.getMessage(), e))
                .onErrorComplete()
                .subscribe();

        return ResultUtil.success();
    }

    /**
     * 通过用户组ID列表刷新所属用户的权限缓存
     * @param groupIdList 用户组ID列表
     */
    private void refreshUserAuthoritiesCacheByGroupIds(Collection<String> groupIdList) {
        List<String> usernames = userDAO.findUsernamesByGroupIdList(groupIdList);
        usernames.forEach(roleService::refreshUserAuthoritiesCache);
    }

    /**
     * 删除用户组
     */
    public ResponseResult<Object> delete(List<String> groupIds) {
        groupDAO.removeByIdIn(groupIds);

        // 异步刷新属于该组的所有用户的权限缓存
        Completable.fromAction(() -> refreshUserAuthoritiesCacheByGroupIds(groupIds))
                .subscribeOn(Schedulers.io())
                .doOnError(e -> log.error("刷新用户组缓存失败: {}", e.getMessage(), e))
                .onErrorComplete()
                .subscribe();

        return ResultUtil.success();
    }

    /**
     * 获取详情
     */
    public ResponseResult<GroupDTO> info(String id) {
        GroupDO groupDO = groupDAO.findById(id).orElse(null);
        if (groupDO == null) {
            return ResultUtil.error("用户组不存在");
        }
        return ResultUtil.success(convertToDTO(groupDO));
    }

    private GroupDTO convertToDTO(GroupDO groupDO) {
        GroupDTO dto = new GroupDTO();
        BeanUtils.copyProperties(groupDO, dto);
        return dto;
    }

    /**
     * 获取该组下的所有用户名列表
     */
    public ResponseResult<List<String>> getAssignedUsernames(String groupId) {
        return ResultUtil.success(userDAO.findUsernamesByGroupIdList(Collections.singleton(groupId)));
    }

    /**
     * 分配用户到组
     */
    public ResponseResult<Object> assignUsers(GroupAssignDTO assignDTO) {
        String groupId = assignDTO.getGroupId();
        List<String> newUsernameList = assignDTO.getUsernameList() == null ? new ArrayList<>() : assignDTO.getUsernameList();

        // 1. 找出当前已经在该组的所有用户
        List<String> oldUsers = userDAO.findUsernamesByGroupIdList(Collections.singleton(groupId));
        Set<String> oldUsernameSet = new HashSet<>(oldUsers);

        // 2. 计算需要移除的用户 (在 old 中但不在 new 中)
        List<String> toRemoveUsernameList = oldUsernameSet.stream()
                .filter(id -> !newUsernameList.contains(id))
                .collect(Collectors.toList());

        // 3. 计算需要新增的用户 (在 new 中但不在 old 中)
        List<String> toAddUsernameList = newUsernameList.stream()
                .filter(id -> !oldUsernameSet.contains(id))
                .collect(Collectors.toList());

        Set<ConsumerDO> updatedUsers = new HashSet<>();
        // 4. 执行移除操作
        if (!toRemoveUsernameList.isEmpty()) {
            List<ConsumerDO> removeUsers = userDAO.findAllByUsername(toRemoveUsernameList);
            for (ConsumerDO user : removeUsers) {
                if (user.getGroups() != null) {
                    user.getGroups().remove(groupId);
                    updatedUsers.add(user);
                }
            }
        }

        // 5. 执行新增操作
        if (!toAddUsernameList.isEmpty()) {
            List<ConsumerDO> addUsers = userDAO.findAllByUsername(toAddUsernameList);
            for (ConsumerDO user : addUsers) {
                if (user.getGroups() == null) {
                    user.setGroups(new ArrayList<>());
                }
                if (!user.getGroups().contains(groupId)) {
                    user.getGroups().add(groupId);
                    updatedUsers.add(user);
                }
            }
        }

        // 6. 批量更新用户并刷新缓存
        updateConsumerCache(updatedUsers);

        return ResultUtil.success();
    }

    /**
     * 更新用户并刷新缓存
     * @param users 用户集合
     */
    private void updateConsumerCache(Set<ConsumerDO> users) {
        userDAO.saveAll(users);
        for (ConsumerDO user : users) {
            // 更新用户缓存
            CaffeineUtil.setConsumerByUsernameCache(user.getUsername(), user);
            // 刷新权限缓存
            roleService.refreshUserAuthoritiesCache(user.getUsername());
        }
    }
}
