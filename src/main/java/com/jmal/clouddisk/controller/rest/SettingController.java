package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.model.UserSetting;
import com.jmal.clouddisk.model.UserSettingDTO;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * @author jmal
 * @Description 设置
 * @Date 2020/10/28 5:29 下午
 */
@RestController
@Api(tags = "设置")
public class SettingController {

    @Autowired
    private SettingService settingService;

    @ApiOperation("把文件同步到数据库")
    @GetMapping("/setting/sync")
    public ResponseResult<Object> list(@RequestParam String username) {
        settingService.sync(username);
        return ResultUtil.success();
    }

    @ApiOperation("获取网站设置")
    @GetMapping("/public/website/setting")
    public ResponseResult<UserSettingDTO> getWebsiteSetting() {
        return ResultUtil.success(settingService.getWebsiteSetting());
    }

    @ApiOperation("更新用户设置")
    @PutMapping("/setting/update")
    public ResponseResult<Object> update(@RequestBody UserSetting userSetting) {
        return settingService.update(userSetting);
    }

    @ApiOperation("生成accessToken")
    @PutMapping("/user/setting/generateAccessToken")
    ResponseResult<String> generateAccessToken(@RequestParam String username) {
        return settingService.generateAccessToken(username);
    }
}
