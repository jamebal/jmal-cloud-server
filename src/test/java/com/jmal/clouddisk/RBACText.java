package com.jmal.clouddisk;

import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author jmal
 * @Description rbac
 * @Date 2021/1/12 3:11 下午
 */
@SpringBootTest
class RBACText {

    @Autowired
    IUserService userService;

    @Test
    void getAuthorities(){
        Console.log("admin", userService.getAuthorities("admin"));
    }
}
