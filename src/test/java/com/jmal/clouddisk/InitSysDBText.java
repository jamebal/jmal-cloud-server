package com.jmal.clouddisk;

import com.jmal.clouddisk.service.impl.MenuService;
import com.jmal.clouddisk.service.impl.RoleService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author jmal
 * @Description 初始化系统管理数据库
 * @Date 2021/1/12 9:19 上午
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class InitSysDBText {

    @Autowired
    MenuService menuService;

    @Autowired
    RoleService roleService;

    /***
     * 通过json文件初始化菜单数据
     */
    @Test
    public void initMenu() {
        menuService.initMenus();
    }

    /***
     * 通过json文件初始化角色数据
     */
    @Test
    public void initRole() {
        roleService.initRoles();
    }
}
