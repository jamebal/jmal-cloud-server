package com.jmal.clouddisk.annotation;

import cn.hutool.core.text.CharSequenceUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @Description 注解工具 (已适配 GraalVM Native Image)
 * @blame jmal
 * @Date 2021/1/8 8:38 下午
 */
@Component
public class AnnoManageUtil {

    /***
     * 权限标识列表
     */
    public static final List<String> AUTHORITIES = new ArrayList<>();

    // 注入 Spring 的应用上下文
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 通过 Spring 容器获取所有的权限标识，不再需要反射扫描。
     */
    @PostConstruct
    public void getAllAuthorities() {
        // 不再需要 Reflections！
        // 直接从 Spring 容器中获取所有被 @RestController 注解的 beans
        Map<String, Object> restControllers = applicationContext.getBeansWithAnnotation(RestController.class);
        Collection<Object> controllerInstances = restControllers.values();

        List<String> arrayList = new ArrayList<>();
        for (Object controller : controllerInstances) {
            // 注意：这里我们拿到的是代理对象，需要获取其真实类型
            Class<?> controllerClass = controller.getClass();
            // 如果你使用了 AOP (如事务)，可能会得到一个代理类，需要获取其父类
            if (controllerClass.getName().contains("$$SpringCGLIB")) {
                controllerClass = controllerClass.getSuperclass();
            }

            // 得到该类下面的所有方法
            Method[] methods = controllerClass.getDeclaredMethods();
            for (Method method : methods) {
                //得到该类下面的Permission注解
                Permission permission = method.getAnnotation(Permission.class);
                if (permission == null || CharSequenceUtil.isBlank(permission.value())) {
                    continue;
                }
                if (!arrayList.contains(permission.value())) {
                    arrayList.add(permission.value());
                }
            }
        }
        AUTHORITIES.addAll(arrayList.stream().sorted().toList());
    }
}
