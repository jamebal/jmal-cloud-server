package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.model.rbac.Personalization;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author jmal
 * @Description 用户设置
 * @Date 2020/12/24 11:21 上午
 */
@RestController
@Tag(name = "用户设置")
@RequiredArgsConstructor
public class UserSettingController {

    private final SettingService settingService;

    private final UserLoginHolder userLoginHolder;

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

    @Operation(summary = "获取个性化配置")
    @GetMapping("/user/setting/personalization")
    @Permission("sys:user:list")
    ResponseResult<Personalization> personalization() {
        return ResultUtil.success(settingService.getPersonalization(userLoginHolder.getUsername()));
    }

    @Operation(summary = "保存个性化配置")
    @PostMapping("/user/setting/personalization")
    @Permission("sys:user:update")
    @LogOperatingFun
    ResponseResult<Object> savePersonalization(@RequestBody Personalization personalization) {
        settingService.savePersonalization(userLoginHolder.getUsername(), personalization);
        return ResultUtil.success();
    }

}
