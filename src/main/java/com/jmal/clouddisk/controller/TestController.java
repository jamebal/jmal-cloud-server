package com.jmal.clouddisk.controller;

import com.jmal.service.JmalService;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description TODO
 * @Author jmal
 * @Date 2020-03-18 15:54
 */
@RestController
public class TestController {

    @Autowired
    JmalService jmalService;

    @RequestMapping("/public/test")
    public Object test(){
        return jmalService.getStatus();
    }
}
