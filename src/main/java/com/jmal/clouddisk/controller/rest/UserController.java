package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description ConsumerController
 */
@RestController
@RequestMapping("user")
@Tag(name = "用户管理")
public class UserController {

    @Autowired
    IUserService service;

    @Operation(summary = "添加用户")
    @PostMapping("/add")
    @Permission("sys:user:add")
    @LogOperatingFun
    public ResponseResult<Object> add(@Validated ConsumerDTO consumerDTO) {
        service.add(consumerDTO);
        return ResultUtil.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/delete")
    @Permission("sys:user:delete")
    @LogOperatingFun
    public ResponseResult<Object> delete(@RequestParam String[] ids) {
        List<String> idList = Arrays.asList(ids);
        return service.delete(idList);
    }

    @Operation(summary = "修改用户")
    @PutMapping("/update")
    @Permission("sys:user:update")
    @LogOperatingFun
    public ResponseResult<Object> update(ConsumerDTO consumerDTO, MultipartFile blobAvatar) {
        return service.update(consumerDTO, blobAvatar);
    }

    @Operation(summary = "修改用户密码")
    @PutMapping("/update-pass")
    @Permission("sys:user:update")
    @LogOperatingFun
    public ResponseResult<Object> updatePass(ConsumerDO consumer) {
        return service.updatePass(consumer);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/reset-pass")
    @Permission(value = "sys:user:update")
    @LogOperatingFun
    public ResponseResult<Object> resetPass(ConsumerDO consumer) {
        return service.resetPass(consumer);
    }

    @Operation(summary = "用户信息")
    @GetMapping("/userInfo")
    @Permission("sys:user:list")
    public ResponseResult<ConsumerDTO> consumerInfo(@RequestParam String id) {
        return service.userInfo(id);
    }

    @Operation(summary = "获取用户名")
    @GetMapping("/username")
    @Permission("cloud:file:list")
    public ResponseResult<String> getUsernameByUserId(@RequestParam String userId) {
        return ResultUtil.success(service.getUserNameById(userId));
    }

    @Operation(summary = "用户信息列表")
    @GetMapping("/userList")
    @Permission("sys:user:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<ConsumerDTO>> consumerList(QueryUserDTO queryDTO) {
        return service.userList(queryDTO);
    }

}
