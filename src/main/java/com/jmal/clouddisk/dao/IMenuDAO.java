package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.List;

public interface IMenuDAO {

    List<MenuDO> treeMenu(QueryMenuDTO queryDTO);

    MenuDO findById(String menuId);

    boolean existsByName(String name);

    void save(MenuDO menuDO);

    boolean existsByNameAndIdNot(@NotNull(message = "菜单名称不能为空") String name, String id);

    List<String> findIdsByParentIdIn(List<String> ids);

    void removeByIdIn(Collection<String> idList);

    List<String> findAuthorityAllByIds(List<String> menuIdList);

    boolean existsById(String id);

    boolean exists();

    void saveAll(List<MenuDO> menuDOList);

    List<MenuDO> findAll();
}
