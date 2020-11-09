package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserSetting;
import com.jmal.clouddisk.model.UserSettingDTO;
import com.jmal.clouddisk.service.impl.UserSettingService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * @author jmal
 * @Description 用户设置
 * @Date 2020/11/5 2:51 下午
 */
@Controller
@Api(tags = "用户设置")
public class UserSettingController {

    @Autowired
    private UserSettingService userSettingService;

    @ApiOperation("获取用户设置")
    @GetMapping("/user/setting")
    @ResponseBody
    public ResponseResult<UserSettingDTO> getSetting(@RequestParam String userId) {
        return userSettingService.getSetting(userId);
    }

    @ApiOperation("更新用户设置")
    @PutMapping("/user/setting/update")
    @ResponseBody
    public ResponseResult<Object> update(@RequestBody UserSetting userSetting) {
        if(userSetting == null || StringUtils.isEmpty(userSetting.getUserId())){
            return ResultUtil.error(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少参数userId");
        }
        return userSettingService.update(userSetting);
    }

}
