package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.ConsumerDO;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录、登出、验证
 *
 * @author jmal
 */
@RestController
@Api(tags = "登录认证")
public class AuthController {

    @Autowired
    IAuthService authService;
    @Autowired
    IUserService userService;

    @ApiOperation("登录")
    @PostMapping("/login")
    public ResponseResult<Object> login(@RequestBody ConsumerDO user){
        return authService.login(user.getUsername(), user.getPassword());
    }

    @ApiOperation("校验旧密码")
    @PostMapping("/valid-old-pass")
    public ResponseResult<Object> validOldPass(@RequestBody ConsumerDO user){
        return authService.validOldPass(user.getId(), user.getPassword());
    }

    @ApiOperation("登出")
    @GetMapping("/logout")
    public ResponseResult<Object> logout(HttpServletRequest request){
        String token = request.getHeader(AuthInterceptor.JMAL_TOKEN);
        return authService.logout(token);
    }

    @ApiOperation("是否有用户")
    @GetMapping("/public/has_user")
    public ResponseResult<Boolean> hasUser(){
        return userService.hasUser();
    }

    @ApiOperation("初始化创建管理员")
    @PostMapping("/public/initialization")
    public ResponseResult<Object> initialization(ConsumerDO consumer){
        return userService.initialization(consumer);
    }
}
