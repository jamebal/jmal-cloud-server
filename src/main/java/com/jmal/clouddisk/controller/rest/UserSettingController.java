package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Autowired
    private UserLoginHolder userLoginHolder;

    @ApiOperation("生成accessToken")
    @PutMapping("/user/setting/generateAccessToken")
    @Permission("sys:user:update")
    @LogOperatingFun
    ResponseResult<String> generateAccessToken(@RequestParam String tokenName) {
        return settingService.generateAccessToken(userLoginHolder.getUsername(), tokenName);
    }

    @ApiOperation("accessToken列表")
    @GetMapping("/user/setting/accessTokenList")
    @Permission("sys:user:list")
    ResponseResult<List<UserAccessTokenDTO>> accessTokenList() {
        return settingService.accessTokenList(userLoginHolder.getUsername());
    }

    @ApiOperation("删除accessToken")
    @DeleteMapping("/user/setting/deleteAccessToken")
    @Permission("sys:user:delete")
    @LogOperatingFun
    ResponseResult<Object> deleteAccessToken(@RequestParam String id) {
        settingService.deleteAccessToken(id);
        return ResultUtil.success();
    }

    @ApiOperation("把文件同步到数据库")
    @GetMapping("/user/setting/sync")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> sync(@RequestParam String username) {
        settingService.sync(username);
        return ResultUtil.success();
    }

    @ApiOperation("重置角色菜单")
    @PutMapping("/user/setting/resetMenuAndRole")
    @Permission(onlyCreator = true)
    @LogOperatingFun
    public ResponseResult<Object> resetMenuAndRole() {
        settingService.resetMenuAndRole();
        return ResultUtil.success();
    }

    @ApiOperation("获取是否禁用webp状态")
    @GetMapping("/user/setting/get/webp")
    @Permission("sys:user:list")
    public ResponseResult<Boolean> getDisabledWebp(@RequestParam String userId) {
        return ResultUtil.success(userService.getDisabledWebp(userId));
    }

    @ApiOperation("是否禁用webp(默认开启)")
    @PutMapping("/user/setting/disabled/webp")
    @Permission("sys:user:update")
    @LogOperatingFun
    public ResponseResult<Object> disabledWebp(@RequestParam String userId, @RequestParam Boolean disabled) {
        userService.disabledWebp(userId, disabled);
        return ResultUtil.success();
    }
}
