package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IMenuDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.GroupRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.MenuRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.RoleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.menu.MenuOperation;
import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.GroupDO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.service.impl.RoleService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class MenuDAOJpaImpl implements IMenuDAO, IWriteCommon<MenuDO> {

    private final IWriteService writeService;
    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Override
    public List<MenuDO> treeMenu(QueryMenuDTO queryDTO) {

        // 使用JPA Specification动态构建查询
        Specification<MenuDO> spec = (root, _, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. 根据菜单名称模糊查询
            if (!CharSequenceUtil.isBlank(queryDTO.getName())) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + queryDTO.getName() + "%"));
            }

            // 2. 根据用户ID和角色ID计算需要查询的菜单ID列表
            Set<String> menuIdList = getPermittedMenuIds(queryDTO);

            // 如果 menuIdList 不为 null，说明需要根据权限进行过滤
            if (menuIdList != null) {
                // 如果计算后有权访问的菜单ID列表为空，则直接返回空结果，无需查询数据库
                if (menuIdList.isEmpty()) {
                    // 返回一个永远为假的条件
                    return criteriaBuilder.disjunction();
                }
                // 添加 in 查询条件
                predicates.add(root.get("id").in(menuIdList));
            }

            // 3. 根据路径模糊查询 (同时匹配 path 和 component)
            if (!CharSequenceUtil.isBlank(queryDTO.getPath())) {
                Predicate pathPredicate = criteriaBuilder.like(root.get("path"), "%" + queryDTO.getPath() + "%");
                Predicate componentPredicate = criteriaBuilder.like(root.get("component"), "%" + queryDTO.getPath() + "%");
                predicates.add(criteriaBuilder.or(pathPredicate, componentPredicate));
            }

            // 组合所有查询条件
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // 执行查询并按 sortNumber 排序
        return menuRepository.findAll(spec, org.springframework.data.domain.Sort.by("sortNumber"));
    }

    @Override
    public MenuDO findById(String menuId) {
        return menuRepository.findById(menuId).orElse(null);
    }

    @Override
    public boolean existsByName(String name) {
        return menuRepository.existsByName(name);
    }

    @Override
    public void save(MenuDO menuDO) {
        CompletableFuture<Void> future = writeService.submit(new MenuOperation.Create(menuDO));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("MenuDAOJpaImpl save error", e);
        }
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String id) {
        return menuRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    public List<String> findIdsByParentIdIn(List<String> ids) {
        return menuRepository.findIdsByParentIdIn(ids);
    }

    @Override
    public void removeByIdIn(Collection<String> idList) {
        CompletableFuture<Void> future = writeService.submit(new MenuOperation.RemoveByIdIn(idList));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("MenuDAOJpaImpl removeByIdIn error", e);
        }
    }

    @Override
    public List<String> findAuthorityAllByIds(List<String> menuIdList) {
        if (menuIdList == null || menuIdList.isEmpty()) {
            return Collections.emptyList();
        }
        return menuRepository.findAuthorityAllByIds(menuIdList);
    }

    @Override
    public boolean existsById(String id) {
        return menuRepository.existsById(id);
    }

    @Override
    public boolean exists() {
        return menuRepository.count() > 0;
    }

    @Override
    public void saveAll(List<MenuDO> menuDOList) {
        writeService.submit(new MenuOperation.CreateAll(menuDOList));
    }

    @Override
    public List<MenuDO> findAll() {
        return menuRepository.findAll();
    }

    private Set<String> findAllMenuId() {
        return menuRepository.findAll().stream()
                .map(MenuDO::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 根据查询DTO获取用户有权访问的菜单ID集合。
     * 迁移自MongoDB实现的逻辑。
     * @param queryDTO 查询DTO
     * @return 有权访问的菜单ID集合。如果为 null，则表示无需根据用户/角色进行权限过滤。
     */
    private Set<String> getPermittedMenuIds(QueryMenuDTO queryDTO) {
        // 如果用户ID和角色ID都为空，则不进行权限过滤，返回null
        if (CharSequenceUtil.isBlank(queryDTO.getUserId()) && CharSequenceUtil.isBlank(queryDTO.getRoleId())) {
            return null;
        }

        Set<String> permittedMenuIds = new HashSet<>();

        // 根据用户ID获取菜单权限
        if (!CharSequenceUtil.isBlank(queryDTO.getUserId())) {
            permittedMenuIds.addAll(getMenuIdListByUserId(queryDTO.getUserId()));
        }

        // 根据角色ID获取菜单权限
        if (!CharSequenceUtil.isBlank(queryDTO.getRoleId())) {
            permittedMenuIds.addAll(getMenuIdListByRoleId(queryDTO.getRoleId()));
        }
        return permittedMenuIds;
    }

    /**
     * 根据用户ID获取其有权访问的菜单ID列表。
     * @param userId 用户ID
     * @return 菜单ID列表
     */
    private Set<String> getMenuIdListByUserId(String userId) {
        Optional<ConsumerDO> consumerOpt = userRepository.findById(userId);
        if (consumerOpt.isEmpty()) {
            return Collections.emptySet();
        }

        ConsumerDO consumer = consumerOpt.get();
        // 如果是超级管理员(creator)，则拥有所有菜单权限
        if (consumer.getCreator() != null && consumer.getCreator()) {
            return findAllMenuId();
        }

        Set<String> roleIdList = new HashSet<>();

        // 添加用户的直接角色
        if (consumer.getRoles() != null && !consumer.getRoles().isEmpty()) {
            roleIdList.addAll(consumer.getRoles());
        }

        // 添加用户所属组的角色
        if (consumer.getGroups() != null && !consumer.getGroups().isEmpty()) {
            Set<GroupDO> groupDOS = groupRepository.findAllByIdIn(consumer.getGroups());
            for (GroupDO groupDO : groupDOS) {
                if (groupDO.getRoles() != null) {
                    roleIdList.addAll(groupDO.getRoles());
                }
            }
        }

        if (isAdministratorsByRoleIds(roleIdList)) {
            // 如果是超级管理员则返回所有菜单
            return findAllMenuId();
        }

        return getMenuIdListByRoleIdList(roleIdList);
    }

    private boolean isAdministratorsByRoleIds(Set<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        List<String> listCode = roleRepository.findAllCodeByIdIn(roleIds);
        return !listCode.isEmpty() && listCode.contains(RoleService.ADMINISTRATORS);
    }

    /**
     * 根据单个角色ID获取其有权访问的菜单ID列表。
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    private Set<String> getMenuIdListByRoleId(String roleId) {
        return getMenuIdListByRoleIdList(Collections.singleton(roleId));
    }

    /**
     * 根据角色ID列表获取这些角色共同拥有的所有菜单ID（去重）。
     * @param roleIdList 角色ID列表
     * @return 去重后的菜单ID集合
     */
    public Set<String> getMenuIdListByRoleIdList(Set<String> roleIdList) {
        Set<String> menuIdList = new HashSet<>();
        Set<List<String>> roleDOList = roleRepository.findMenuIdsByIdIn(roleIdList);
        roleDOList.forEach(menuIdList::addAll);
        return menuIdList;
    }

    @Override
    public void AsyncSaveAll(Iterable<MenuDO> entities) {
        writeService.submit(new MenuOperation.CreateAll(entities));
    }
}
