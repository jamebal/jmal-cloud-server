package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jmal
 * @Description 用户设置
 * @Date 2020/12/24 11:21 上午
 */
@RestController
@Api(tags = "用户设置")
public class UserSettingController {

    @Autowired
    private SettingService settingService;

    @Autowired
    private IUserService userService;

    @ApiOperation("生成accessToken")
    @PutMapping("/user/setting/generateAccessToken")
    ResponseResult<String> generateAccessToken(@RequestParam String username) {
        return settingService.generateAccessToken(username);
    }

    @ApiOperation("把文件同步到数据库")
    @GetMapping("/user/setting/sync")
    public ResponseResult<Object> list(@RequestParam String username) {
        settingService.sync(username);
        return ResultUtil.success();
    }

    @ApiOperation("获取是否禁用webp状态")
    @GetMapping("/user/setting/get/webp")
    public ResponseResult<Boolean> getDisabledWebp(@RequestParam String userId) {
        return ResultUtil.success(userService.getDisabledWebp(userId));
    }

    @ApiOperation("是否禁用webp(默认开启)")
    @PutMapping("/user/setting/disabled/webp")
    public ResponseResult<Object> disabledWebp(@RequestParam String userId, @RequestParam Boolean disabled) {
        userService.disabledWebp(userId, disabled);
        return ResultUtil.success();
    }
}
