package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.model.ConsumerDO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseResult<Object> add(ConsumerDO consumer){
        return service.add(consumer);
    }

    @ApiOperation(value = "删除用户")
    @DeleteMapping("/delete")
    public ResponseResult<Object> delete(@RequestParam String id){
        return service.delete(id);
    }

    @ApiOperation(value = "修改用户")
    @PutMapping("/update")
    public ResponseResult<Object> update(ConsumerDO consumer, MultipartFile blobAvatar){
        return service.update(consumer,blobAvatar);
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
    public ResponseResult<Object> consumerList(){
        return service.userList();
    }

}
