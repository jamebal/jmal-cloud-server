package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.model.Consumer;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description ConsumerController
 * @blame jmal
 */
@Controller
@RequestMapping("user")
@Api(tags = "用户管理")
public class UserController {

    @Autowired
    IUserService service;

    @ApiOperation(value = "添加用户")
    @PostMapping("/add")
    @ResponseBody
    public ResponseResult<Object> add(@RequestBody Consumer Consumer){
        return service.add(Consumer);
    }

    @ApiOperation(value = "删除用户")
    @GetMapping("/delete")
    @ResponseBody
    public ResponseResult<Object> delete(@RequestParam String id){
        return service.delete(id);
    }

    @ApiOperation(value = "修改用户")
    @PostMapping("/update")
    @ResponseBody
    public ResponseResult<Object> update(Consumer Consumer, MultipartFile blobAvatar){
        return service.update(Consumer,blobAvatar);
    }

    @ApiOperation(value = "用户信息")
    @GetMapping("/userInfo")
    @ResponseBody
    public ResponseResult<Object> ConsumerInfo(@RequestParam String id,Boolean takeUpSpace){
        return service.userInfo(id, takeUpSpace);
    }

    @ApiOperation(value = "用户信息列表")
    @GetMapping("/userList")
    @ResponseBody
    public ResponseResult<Object> ConsumerList(){
        return service.userList();
    }
}
