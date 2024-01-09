package com.jmal.clouddisk;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONArray;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import com.jmal.clouddisk.service.impl.MenuService;
import com.jmal.clouddisk.service.impl.RoleService;
import com.jmal.clouddisk.util.TimeUntils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jmal
 * @Description 将系统管理数据库导出为json
 * @Date 2021/1/12 9:19 上午
 */
@SpringBootTest
class ExportSysDBText {

    @Autowired
    MenuService menuService;

    @Autowired
    RoleService roleService;

    /***
     * 把菜单导出为json文件
     */
    @Test
    void exportMenu() {
        String now = LocalDateTimeUtil.formatNormal(LocalDateTime.now(TimeUntils.ZONE_ID));
        File file = new File("/Users/jmal/Downloads/menu"+now+".json");
        List<MenuDO> menuDOList = menuService.getAllMenus();
        if(menuDOList.size() > 0){
            FileUtil.writeString(JSONArray.toJSONString(menuDOList),file, StandardCharsets.UTF_8);
        }
    }

    /***
     * 把角色导出为json文件
     */
    @Test
    void exportRole() {
        String now = LocalDateTimeUtil.formatNormal(LocalDateTime.now(TimeUntils.ZONE_ID));
        File file = new File("/Users/jmal/Downloads/role"+now+".json");
        List<RoleDO> roleDOList = roleService.getAllRoles();
        if(roleDOList.size() > 0){
            FileUtil.writeString(JSONArray.toJSONString(roleDOList),file,StandardCharsets.UTF_8);
        }
    }
}
