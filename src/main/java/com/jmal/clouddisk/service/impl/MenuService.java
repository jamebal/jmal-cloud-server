package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.IMenuDAO;
import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MenuService {

    public static final String COLLECTION_NAME = "menu";

    private final DataSourceProperties dataSourceProperties;

    private final MessageUtil messageUtil;

    private final IMenuDAO menuDAO;

    /***
     * 菜单树
     * @param queryDTO QueryMenuDTO
     * @return 菜单数列表
     */
    public List<MenuDTO> tree(QueryMenuDTO queryDTO) {
        List<MenuDO> menuDOList = menuDAO.treeMenu(queryDTO);
        Locale locale = LocaleContextHolder.getLocale();
        List<MenuDTO> menuDTOList = menuDOList.stream().map(menuDO -> {
            MenuDTO menuDTO = menuDO.toDTO();
            menuDTO.setName(messageUtil.getMessage(menuDO.getName(), locale));
            return menuDTO;
        }).toList();
        return getSubMenu(null, menuDTOList);
    }

    /**
     * 查找子菜单
     *
     * @param parentId 父菜单id
     * @param menuDTOList  菜单列表
     * @return 菜单列表
     */
    private List<MenuDTO> getSubMenu(String parentId, List<MenuDTO> menuDTOList) {
        List<MenuDTO> menuDTOTreeList = new ArrayList<>();
        List<MenuDTO> menuList;
        if (CharSequenceUtil.isBlank(parentId)) {
            menuList = menuDTOList.stream().filter(menuDTO ->
                    CharSequenceUtil.isBlank(menuDTO.getParentId())).sorted().toList();
        } else {
            menuList = menuDTOList.stream().filter(menuDTO -> parentId.equals(menuDTO.getParentId())).sorted().toList();
        }
        menuList.forEach(subCategory -> {
            List<MenuDTO> subList = getSubMenu(subCategory.getId(), menuDTOList);
            if (!subList.isEmpty()) {
                subCategory.setChildren(subList);
            }
            menuDTOTreeList.add(subCategory);
        });
        return menuDTOTreeList;
    }

    /***
     * 通过菜单Id获取菜单
     * @param menuId 菜单Id
     * @return 一个菜单信息
     */
    public MenuDO getMenuInfo(String menuId) {
        return menuDAO.findById(menuId);
    }

    /***
     * 菜单名是否存在
     * @param name name
     * @return boolean
     */
    private boolean existsMenuName(String name){
        return menuDAO.existsByName(name);
    }

    /***
     * 添加菜单
     * @param menuDTO menuDTO
     * @return ResponseResult
     */
    public ResponseResult<Object> add(MenuDTO menuDTO) {
        if (existsMenuName(menuDTO.getName())) {
            return ResultUtil.warning("该菜单名称已存在");
        }
        if (!CharSequenceUtil.isBlank(menuDTO.getParentId())) {
            MenuDO menuDO = getMenuInfo(menuDTO.getParentId());
            if (menuDO == null) {
                return ResultUtil.warning("该父分级菜单不存在");
            }
        }
        MenuDO menuDO = new MenuDO();
        BeanUtils.copyProperties(menuDTO, menuDO);
        menuDO.setId(null);
        LocalDateTime dateNow = LocalDateTime.now(TimeUntils.ZONE_ID);
        menuDO.setCreateTime(dateNow);
        menuDO.setUpdateTime(dateNow);
        menuDAO.save(menuDO);
        return ResultUtil.success();
    }

    /***
     * 更新菜单
     * @param menuDTO menuDTO
     * @return ResponseResult
     */
    public ResponseResult<Object> update(MenuDTO menuDTO) {
        if (getMenuInfo(menuDTO.getId()) == null) {
            return ResultUtil.warning("该菜单不存在");
        }
        if(menuDAO.existsByNameAndIdNot(menuDTO.getName(), menuDTO.getId())){
            return ResultUtil.warning("该菜单名称已存在");
        }
        MenuDO menuDO = new MenuDO();
        BeanUtils.copyProperties(menuDTO, menuDO);
        menuDO.setUpdateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
        menuDAO.save(menuDO);
        return ResultUtil.success();
    }

    /***
     * 删除菜单及其下的所有子菜单
     * @param menuIdList 菜单Id列表
     */
    public void delete(List<String> menuIdList) {
        Set<String> menuIds = findSelfAndAllDescendantIds(menuIdList);
        // 删除所有关联的菜单
        menuDAO.removeByIdIn(menuIds);
    }

    /**
     * 获取指定菜单ID列表及其所有子孙菜单的ID。
     * 此方法使用Java迭代查询实现，不依赖任何数据库的特定语法，
     * 保证了在MySQL, PostgreSQL, SQLite等多种数据库上的可移植性。
     *
     * @param initialMenuIds 初始的菜单ID列表
     * @return 包含初始ID及其所有后代ID的列表 (已去重)
     */
    public Set<String> findSelfAndAllDescendantIds(List<String> initialMenuIds) {
        if (initialMenuIds == null || initialMenuIds.isEmpty()) {
            return Collections.emptySet();
        }

        final Set<String> allIds = new HashSet<>(initialMenuIds);

        // 使用Deque（双端队列）作为待处理队列，存放需要查找其子节点的父节点ID。
        // 这是实现广度优先搜索（BFS）的标准数据结构。
        final Deque<String> idsToSearch = new ArrayDeque<>(initialMenuIds);

        while (!idsToSearch.isEmpty()) {
            List<String> currentBatchToSearch = new ArrayList<>(idsToSearch);
            idsToSearch.clear();

            List<String> childIds = menuDAO.findIdsByParentIdIn(currentBatchToSearch);

            if (!childIds.isEmpty()) {
                for (String childId : childIds) {
                    // add方法如果元素已存在，会返回false。
                    // 这样可以防止因数据问题（如循环引用）导致的无限循环。
                    if (allIds.add(childId)) {
                        // 如果是一个全新的ID，则将其加入下一次的待查找队列
                        idsToSearch.add(childId);
                    }
                }
            }
        }

        return allIds;
    }

    /***
     * 获取权限列表
     * @param menuIdList 菜单id列表
     * @return 权限列表
     */
    public List<String> getAuthorities(List<String> menuIdList) {
        return menuDAO.findAuthorityAllByIds(menuIdList);
    }

    /***
     * 初始化菜单数据
     */
    public void initMenus() {
        if (dataSourceProperties.getMigration()) {
            return;
        }
        TimeInterval timeInterval = new TimeInterval();
        List<MenuDO> menuDOList = getMenuDOListByConfigJSON();
        if (menuDOList.isEmpty()) return;
        // 提取出需要更新的菜单
        List<MenuDO> needUpdateMenuList = new ArrayList<>();
        menuDOList.forEach(menuDO -> {
            if (!menuDAO.existsById(menuDO.getId())) {
                needUpdateMenuList.add(menuDO);
            }
        });
        if (needUpdateMenuList.isEmpty()) return;
        menuDAO.saveAll(needUpdateMenuList);
        log.info("更新菜单， 耗时:{}ms", timeInterval.intervalMs());
    }

    /**
     * 从配置文件读取菜单数据
     */
    private static List<MenuDO> getMenuDOListByConfigJSON() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("db/menu.json");
        if(inputStream == null){
                return Collections.emptyList();
        }
        String json = new String(IoUtil.readBytes(inputStream), StandardCharsets.UTF_8);
        return JacksonUtil.parseArray(json, MenuDO.class);
    }

    /***
     * 是否存在菜单
     */
    public boolean existsMenu(){
       return menuDAO.exists();
    }
}
