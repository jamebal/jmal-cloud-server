package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson2.JSON;
import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.util.Locale;

/**
 * @Description 菜单管理
 * &#064;blame  jmal
 * @Date 2021/1/7 7:45 下午
 */
@Service
@Slf4j
public class MenuService {

    public static final String COLLECTION_NAME = "menu";

    @Autowired
    private RoleService roleService;

    @Autowired
    private IUserService userService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MessageUtil messageUtil;

    /***
     * 菜单树
     * @param queryDTO QueryMenuDTO
     * @return 菜单数列表
     */
    public List<MenuDTO> tree(QueryMenuDTO queryDTO) {
        Query query = new Query();
        if(!CharSequenceUtil.isBlank(queryDTO.getName())){
            query.addCriteria(Criteria.where("name").regex(queryDTO.getName(), "i"));
        }
        List<String> menuIdList = null;
        if(!CharSequenceUtil.isBlank(queryDTO.getUserId())){
            menuIdList = userService.getMenuIdList(queryDTO.getUserId());
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getRoleId())) {
            if(menuIdList != null){
                menuIdList.addAll(roleService.getMenuIdList(queryDTO.getRoleId()));
            } else {
                menuIdList = roleService.getMenuIdList(queryDTO.getRoleId());
            }
        }
        if(menuIdList != null){
            query.addCriteria(Criteria.where("_id").in(menuIdList));
        }
        if(!CharSequenceUtil.isBlank(queryDTO.getPath())){
            query.addCriteria(Criteria.where("path").regex(queryDTO.getPath(), "i"));
            query.addCriteria(Criteria.where("component").regex(queryDTO.getPath(), "i"));
        }
        List<MenuDO> menuDOList = mongoTemplate.find(query, MenuDO.class, COLLECTION_NAME);
        Locale locale = LocaleContextHolder.getLocale();
        List<MenuDTO> menuDTOList = menuDOList.parallelStream().map(menuDO -> {
            MenuDTO menuDTO = new MenuDTO();
            BeanUtils.copyProperties(menuDO, menuDTO);
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
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(menuId));
        return mongoTemplate.findOne(query, MenuDO.class, COLLECTION_NAME);
    }

    /***
     * 菜单名是否存在
     * @param name name
     * @return boolean
     */
    private boolean existsMenuName(String name){
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(name));
        return mongoTemplate.exists(query, COLLECTION_NAME);
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
        mongoTemplate.save(menuDO, COLLECTION_NAME);
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
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(menuDTO.getId()));
        query1.addCriteria(Criteria.where("name").is(menuDTO.getName()));
        if(mongoTemplate.exists(query1, COLLECTION_NAME)){
            return ResultUtil.warning("该菜单名称已存在");
        }
        MenuDO menuDO = new MenuDO();
        BeanUtils.copyProperties(menuDTO, menuDO);
        menuDO.setUpdateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
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
        List<String> menuIdList1 = mongoTemplate.find(query, MenuDO.class, COLLECTION_NAME).stream().map(MenuDO::getId).toList();
        if (!menuIdList1.isEmpty()) {
            menuIds.addAll(findLoopMenu(false, menuIdList1));
        }
        return menuIds;
    }

    /***
     * 获取权限列表
     * @param menuIdList 菜单id列表
     * @return 权限列表
     */
    public List<String> getAuthorities(List<String> menuIdList) {
        List<String> authorities = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(menuIdList));
        List<MenuDO> menuDOList = mongoTemplate.find(query, MenuDO.class, COLLECTION_NAME);
        menuDOList.forEach(menuDO -> {
            if(!CharSequenceUtil.isBlank(menuDO.getAuthority())){
                authorities.add(menuDO.getAuthority());
            }
        });
        return authorities;
    }

    /***
     * 初始化菜单数据
     */
    public void initMenus() {
        ThreadUtil.execute(() -> {
            TimeInterval timeInterval = new TimeInterval();
            List<MenuDO> menuDOList = getMenuDOListByConfigJSON();
            if (menuDOList.isEmpty()) return;
            menuDOList.parallelStream().forEach(menuDO -> {
                Query query = new Query();
                query.addCriteria(Criteria.where("_id").is(menuDO.getId()));
                boolean exists = mongoTemplate.exists(query, COLLECTION_NAME);
                if (!exists) {
                    mongoTemplate.insert(menuDO);
                }
            });
            log.info("更新菜单， 耗时:{}ms", timeInterval.intervalMs());
        });
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
        return JSON.parseArray(json,MenuDO.class);
    }

    /***
     * 获取所有菜单
     * @return List<MenuDO>
     */
    public List<MenuDO> getAllMenus() {
        return mongoTemplate.findAll(MenuDO.class, COLLECTION_NAME);
    }

    /***
     * 获取所有菜单Id
     * @return List<String>
     */
    public List<String> getAllMenuIdList() {
        List<MenuDO> menuDOList = getAllMenus();
        return menuDOList.stream().map(MenuDO::getId).toList();
    }

    /***
     * 是否存在菜单
     */
    public boolean existsMenu(){
       return mongoTemplate.exists(new Query(), COLLECTION_NAME);
    }
}
