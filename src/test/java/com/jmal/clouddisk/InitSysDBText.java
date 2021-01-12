package com.jmal.clouddisk;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.extra.cglib.CglibUtil;
import com.alibaba.fastjson.JSONArray;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.service.impl.MenuService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

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

    /***
     * 通过json文件初始化菜单数据
     */
    @Test
    public void initMenu() {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("db/menu.json");
        if(inputStream == null){
            return;
        }
        String json = new String(IoUtil.readBytes(inputStream), StandardCharsets.UTF_8);
        List<MenuDO> menuDOList = JSONArray.parseArray(json,MenuDO.class);
        menuService.initMenus(menuDOList);
    }
}
