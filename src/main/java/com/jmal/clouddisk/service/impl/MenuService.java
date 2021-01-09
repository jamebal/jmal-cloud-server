package com.jmal.clouddisk.service.impl;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description 菜单管理
 * @blame jmal
 * @Date 2021/1/7 7:45 下午
 */
@Service
public class MenuService {

    public static final String COLLECTION_NAME = "menu";

    @Autowired
    private RoleService roleService;

    @Autowired
    private MongoTemplate mongoTemplate;

    /***
     * 菜单树
     * @param roleId 角色Id
     * @return
     */
    public List<MenuDTO> tree(String roleId) {
        Query query = new Query();
        List<MenuDO> menuDOList = mongoTemplate.find(query, MenuDO.class, COLLECTION_NAME);
        List<String> menuIdList = null;
        if(!StringUtils.isEmpty(roleId)) {
            menuIdList = roleService.getMenuIdList(roleId);
        }
        List<String> finalMenuIdList = menuIdList;
        List<MenuDTO> menuDTOList = menuDOList.parallelStream().map(menuDO -> {
            MenuDTO menuDTO = new MenuDTO();
            CglibUtil.copy(menuDO, menuDTO);
            if(finalMenuIdList != null){
                menuDTO.setChecked(finalMenuIdList.contains(menuDTO.getId()));
            }
            return menuDTO;
        }).collect(Collectors.toList());
        List<MenuDTO> menuTreeList = getSubMenu(null, menuDTOList);
        return menuTreeList;
    }

    /**
     * 查找子菜单
     *
     * @param parentId 父菜单id
     * @param categoryDTOList  菜单列表
     * @return 菜单列表
     */
    private List<MenuDTO> getSubMenu(String parentId, List<MenuDTO> menuDTOList) {
        List<MenuDTO> menuDTOTreeList = new ArrayList<>();
        List<MenuDTO> menuList;
        if (StringUtils.isEmpty(parentId)) {
            menuList = menuDTOList.stream().filter(menuDTO ->
                    StringUtils.isEmpty(menuDTO.getParentId())).sorted().collect(Collectors.toList());
        } else {
            menuList = menuDTOList.stream().filter(menuDTO -> parentId.equals(menuDTO.getParentId())).collect(Collectors.toList());
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
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(menuId));
        return mongoTemplate.findOne(query, MenuDO.class, COLLECTION_NAME);
    }

    /***
     * 菜单名是否存在
     * @param name
     * @return
     */
    private boolean existsMenuName(String name){
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(name));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    /***
     * 添加菜单
     * @param menuDTO
     * @return
     */
    public ResponseResult<Object> add(MenuDTO menuDTO) {
        if (existsMenuName(menuDTO.getName())) {
            return ResultUtil.warning("该菜单名称已存在");
        }
        if (!StringUtils.isEmpty(menuDTO.getParentId())) {
            MenuDO menuDO = getMenuInfo(menuDTO.getParentId());
            if (menuDO == null) {
                return ResultUtil.warning("该父分级菜单不存在");
            }
        }
        MenuDO menuDO = new MenuDO();
        CglibUtil.copy(menuDTO, menuDO);
        menuDO.setId(null);
        mongoTemplate.save(menuDO, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 更新菜单
     * @param menuDTO
     * @return
     */
    public ResponseResult<Object> update(MenuDTO menuDTO) {
        if (getMenuInfo(menuDTO.getId()) == null) {
            return ResultUtil.warning("该菜单不存在");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(menuDTO.getId()));
        query1.addCriteria(Criteria.where("name").is(menuDTO.getName()));
        if(mongoTemplate.exists(query1, COLLECTION_NAME)){
            return ResultUtil.warning("该菜单名称已存在");
        }
        MenuDO menuDO = new MenuDO();
        CglibUtil.copy(menuDTO, menuDO);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(menuDTO.getId()));
        Update update = MongoUtil.getUpdate(menuDO);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除菜单及其下的所有子菜单
     * @param menuIdList 菜单Id列表
     */
    public void delete(List<String> menuIdList) {
        List<String> menuIds = findLoopMenu(true, menuIdList);
        // 删除所有关联的菜单
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(menuIds));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    /***
     * 递归查找菜单id及其子菜单id列表
     * @param firstFind 是否是第一次查找
     * @param menuIdList 菜单Id列表
     */
    private List<String> findLoopMenu(boolean firstFind, List<String> menuIdList) {
        final List<String> menuIds = new ArrayList<>();
        Query query = new Query();
        if (firstFind) {
            query.addCriteria(Criteria.where("_id").in(menuIdList));
        } else {
            query.addCriteria(Criteria.where("parentId").in(menuIdList));
            menuIds.addAll(menuIdList);
        }
        List<String> menuIdList1 = mongoTemplate.find(query, MenuDO.class, COLLECTION_NAME).stream().map(MenuDO::getId).collect(Collectors.toList());
        if (menuIdList1.size() > 0) {
            menuIds.addAll(findLoopMenu(false, menuIdList1));
        }
        return menuIds;
    }

    /***
     * 获取权限列表
     * @param menuIdList 菜单id列表
     * @return
     */
    public List<String> getAuthorities(List<String> menuIdList) {
        List<String> authorities = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(menuIdList));
        List<MenuDO> menuDOList = mongoTemplate.find(query, MenuDO.class, COLLECTION_NAME);
        menuDOList.stream().forEach(menuDO -> {
            if(menuDO.getAuthority() != null){
                authorities.add(menuDO.getAuthority());
            }
        });
        return authorities;
    }
}
