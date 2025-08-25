package com.jmal.clouddisk.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * @author jmal
 * @Description 系统信息
 * @date 2022/4/13 14:13
 */
@Slf4j
public class SystemUtil {

    /**
     * 获取硬盘可用空间(Gb)
     */
    public static long getFreeSpace(){
        File win = new File("/");
        if (win.exists()) {
            long freeSpace = win.getFreeSpace();
            return freeSpace/1024/1024/1024;
        }
        return 0;
    }

    /**
     * 获取最新版本号
     * @return String
     */
    public static String getNewVersion() {
        String result;
        try {
            result = HttpUtil.get("https://api.github.com/repos/jamebal/jmal-cloud-view/releases/latest");
            if (result == null) {
                return null;
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            return null;
        }
        JSONObject jsonObject = JSON.parseObject(result);
        return Convert.toStr(jsonObject.get("tag_name"));
    }
}
