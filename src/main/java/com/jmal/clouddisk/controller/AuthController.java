package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.AuthInterceptor;
import com.jmal.clouddisk.model.User;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录、登出、验证
 *
 * @blame jmal
 */
@Controller
@Api(tags = "登录认证")
public class AuthController {

    @Autowired
    IAuthService authService;

    @PostMapping("/login")
    @ResponseBody
    public ResponseResult<Object> login(@RequestBody User user){
        return authService.login(user.getUsername(), user.getPassword());
    }

    @GetMapping("/logout")
    @ResponseBody
    public ResponseResult<Object> logout(HttpServletRequest request){
        String token = request.getHeader(AuthInterceptor.JMAL_TOKEN);
        return authService.logout(token);
    }
}
