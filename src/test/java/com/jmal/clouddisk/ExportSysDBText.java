package com.jmal.clouddisk;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSONArray;
import com.jmal.clouddisk.model.rbac.MenuDO;
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
 * @Description 将系统管理数据库导出为json
 * @Date 2021/1/12 9:19 上午
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ExportSysDBText {

    @Autowired
    MenuService menuService;

    /***
     * 把菜单导出为json文件
     */
    @Test
    public void exportMenu() {
        String now = LocalDateTimeUtil.formatNormal(LocalDateTime.now());
        File file = new File("/Users/jmal/Downloads/menu"+now+".json");
        List<MenuDO> menuDOList = menuService.getAllMenus();
        if(menuDOList.size() > 0){
            FileUtil.writeString(JSONArray.toJSONString(menuDOList),file,StandardCharsets.UTF_8);
        }
    }
}
