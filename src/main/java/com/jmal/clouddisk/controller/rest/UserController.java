package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * @Description ConsumerController
 * @author jmal
 */
@RestController
@RequestMapping("user")
@Api(tags = "用户管理")
public class UserController {

    @Autowired
    IUserService service;

    @ApiOperation(value = "添加用户")
    @PostMapping("/add")
    public ResponseResult<Object> add(@Validated ConsumerDTO consumerDTO){
        return service.add(consumerDTO);
    }

    @ApiOperation(value = "删除用户")
    @DeleteMapping("/delete")
    public ResponseResult<Object> delete(@RequestParam String[] ids){
        List<String> idList = Arrays.asList(ids);
        return service.delete(idList);
    }

    @ApiOperation(value = "修改用户")
    @PutMapping("/update")
    public ResponseResult<Object> update(ConsumerDTO consumerDTO, MultipartFile blobAvatar){
        return service.update(consumerDTO, blobAvatar);
    }

    @ApiOperation(value = "修改用户密码")
    @PutMapping("/update-pass")
    public ResponseResult<Object> updatePass(ConsumerDO consumer){
        return service.updatePass(consumer);
    }

    @ApiOperation(value = "重置密码")
    @PutMapping("/reset-pass")
    public ResponseResult<Object> resetPass(ConsumerDO consumer){
        return service.resetPass(consumer);
    }

    @ApiOperation(value = "用户信息")
    @GetMapping("/userInfo")
    public ResponseResult<Object> consumerInfo(@RequestParam String id,Boolean takeUpSpace,Boolean returnPassWord){
        return service.userInfo(id, takeUpSpace, returnPassWord);
    }

    @ApiOperation(value = "用户信息列表")
    @GetMapping("/userList")
    public ResponseResult<List<ConsumerDTO>> consumerList(QueryUserDTO queryDTO){
        return service.userList(queryDTO);
    }

}
