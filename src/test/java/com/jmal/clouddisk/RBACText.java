package com.jmal.clouddisk;

import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.service.IUserService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author jmal
 * @Description rbac
 * @Date 2021/1/12 3:11 下午
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RBACText {

    @Autowired
    IUserService userService;

    @Test
    public void getAuthorities(){
        Console.log("admin", userService.getAuthorities("admin"));
    }
}
