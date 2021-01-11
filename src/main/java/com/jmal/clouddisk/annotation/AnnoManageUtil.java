package com.jmal.clouddisk.annotation;

import org.reflections.Reflections;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @Description 注解工具
 * @blame jmal
 * @Date 2021/1/8 8:38 下午
 */
@Component
public class AnnoManageUtil {

    /***
     * 权限标识列表
     */
    public static final List<String> authorities = new ArrayList<>();
 
    /**
     * 通过反射获取所有的权限标识
     *
     * @return
     */
    @PostConstruct
    public void getAllAuthorities() {
        Reflections reflections = new Reflections("com.jmal.clouddisk.controller");
        Set<Class<?>> classesList = reflections.getTypesAnnotatedWith(RestController.class);
        for (Class classes : classesList) {
            //得到该类下面的所有方法
            Method[] methods = classes.getDeclaredMethods();
            for (Method method : methods) {
                //得到该类下面的Permission注解
                Permission permission = method.getAnnotation(Permission.class);
                if (null != permission) {
                    if(!StringUtils.isEmpty(permission.value())){
                        authorities.add(permission.value());
                    }
                }
            }
        }
    }
 
}