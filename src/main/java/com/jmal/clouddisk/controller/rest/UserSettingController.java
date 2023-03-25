package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author jmal
 * @Description 用户设置
 * @Date 2020/12/24 11:21 上午
 */
@RestController
@Tag(name = "用户设置")
public class UserSettingController {

    @Autowired
    private SettingService settingService;

    @Autowired
    private IUserService userService;

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Operation(summary = "生成accessToken")
    @PutMapping("/user/setting/generateAccessToken")
    @Permission("sys:user:update")
    @LogOperatingFun
    ResponseResult<String> generateAccessToken(@RequestParam String tokenName) {
        return settingService.generateAccessToken(userLoginHolder.getUsername(), tokenName);
    }

    @Operation(summary = "accessToken列表")
    @GetMapping("/user/setting/accessTokenList")
    @Permission("sys:user:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    ResponseResult<List<UserAccessTokenDTO>> accessTokenList() {
        return settingService.accessTokenList(userLoginHolder.getUsername());
    }

    @Operation(summary = "删除accessToken")
    @DeleteMapping("/user/setting/deleteAccessToken")
    @Permission("sys:user:delete")
    @LogOperatingFun
    ResponseResult<Object> deleteAccessToken(@RequestParam String id) {
        settingService.deleteAccessToken(id);
        return ResultUtil.success();
    }

    @Operation(summary = "把文件同步到数据库")
    @GetMapping("/user/setting/sync")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> sync(@RequestParam String username) {
        return settingService.sync(username);
    }

    @Operation(summary = "上传网盘logo")
    @PostMapping("/user/setting/upload_logo")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> uploadLogo(MultipartFile file) {
        return settingService.uploadLogo(file);
    }

    @Operation(summary = "修改网盘名称")
    @PutMapping("/user/setting/update_netdisk_name")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> updateNetdiskName(@RequestParam String netdiskName) {
        return settingService.updateNetdiskName(netdiskName);
    }

    @Operation(summary = "是否正在同步")
    @GetMapping("/user/setting/isSync")
    @LogOperatingFun
    public ResponseResult<Object> isSync(@RequestParam String username) {
        return settingService.isSync(username);
    }

    @Operation(summary = "重置角色菜单")
    @PutMapping("/user/setting/resetMenuAndRole")
    @Permission(onlyCreator = true)
    @LogOperatingFun
    public ResponseResult<Object> resetMenuAndRole() {
        settingService.resetMenuAndRole();
        return ResultUtil.success();
    }

    @Operation(summary = "获取是否禁用webp状态")
    @GetMapping("/user/setting/get/webp")
    @Permission("sys:user:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Boolean> getDisabledWebp(@RequestParam String userId) {
        return ResultUtil.success(userService.getDisabledWebp(userId));
    }

    @Operation(summary = "是否禁用webp(默认开启)")
    @PutMapping("/user/setting/disabled/webp")
    @Permission("sys:user:update")
    @LogOperatingFun
    public ResponseResult<Object> disabledWebp(@RequestParam String userId, @RequestParam Boolean disabled) {
        userService.disabledWebp(userId, disabled);
        return ResultUtil.success();
    }
}
