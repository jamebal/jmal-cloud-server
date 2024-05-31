package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LdapConfigDTO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "网盘设置")
@RequiredArgsConstructor
public class CloudSettingController {

    private final SettingService settingService;

    private final IUserService userService;

    private final IAuthService authService;

    @Operation(summary = "重建索引")
    @GetMapping("/user/setting/sync")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> sync() {
        return settingService.sync(null);
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

    @Operation(summary = "加载ldap配置")
    @LogOperatingFun(logType = LogOperation.Type.LOGIN)
    @GetMapping("/ldap/config")
    public ResponseResult<Object> loadLdapConfig() {
        return ResultUtil.success(authService.loadLdapConfig());
    }

    @Operation(summary = "ldap配置")
    @LogOperatingFun(logType = LogOperation.Type.LOGIN)
    @PutMapping("/ldap/config")
    public ResponseResult<Object> updateLdapConfig(@RequestBody LdapConfigDTO ldapConfigDTO) {
        return authService.updateLdapConfig(ldapConfigDTO);
    }

    @Operation(summary = "测试ldap配置")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @PutMapping("/ldap/test-config")
    public ResponseResult<Object> testLdapConfig(@RequestBody LdapConfigDTO ldapConfigDTO) {
        authService.testLdapConfig(ldapConfigDTO);
        return ResultUtil.success();
    }

}
