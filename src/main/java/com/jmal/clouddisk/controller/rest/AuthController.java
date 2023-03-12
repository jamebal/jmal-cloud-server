package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、登出、验证
 *
 * @author jmal
 */
@RestController
@Tag(name = "登录认证")
public class AuthController {

    @Autowired
    IAuthService authService;
    @Autowired
    IUserService userService;

    @Operation(summary = "登录")
    @LogOperatingFun(logType = LogOperation.Type.LOGIN)
    @PostMapping("/login")
    public ResponseResult<Object> login(@RequestBody ConsumerDTO userDTO, HttpServletRequest request, HttpServletResponse response){
        return authService.login(request, response, userDTO);
    }

    @Operation(summary = "校验旧密码")
    @PostMapping("/valid-old-pass")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @Permission("sys:user:list")
    public ResponseResult<Object> validOldPass(@RequestBody ConsumerDO user){
        return authService.validOldPass(user.getId(), user.getPassword());
    }

    @Operation(summary = "登出")
    @GetMapping("/logout")
    @LogOperatingFun
    @Permission("sys:user:list")
    public ResponseResult<Object> logout(HttpServletRequest request, HttpServletResponse response){
        String token = request.getHeader(AuthInterceptor.JMAL_TOKEN);
        return authService.logout(token, response);
    }

    @Operation(summary = "是否有用户")
    @GetMapping("/public/has_user")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Boolean> hasUser(){
        return userService.hasUser();
    }

    @Operation(summary = "初始化创建管理员")
    @PostMapping("/public/initialization")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> initialization(ConsumerDO consumer){
        return userService.initialization(consumer);
    }
}
