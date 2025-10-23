package com.jmal.clouddisk.annotation;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnnoManageUtil implements ApplicationListener<ContextRefreshedEvent> {

    /***
     * 权限标识列表
     */
    public static final List<String> AUTHORITIES = new ArrayList<>();

    // 注入 Spring 的应用上下文
    private final ApplicationContext applicationContext;

    /**
     * 监听 ContextRefreshedEvent 事件。
     * 此方法将在Spring容器完全初始化所有Bean后被调用。
     * 这是执行应用级别扫描和初始化的安全时机。
     *
     * @param event 上下文刷新事件
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保只在根应用上下文执行一次，防止在Web环境中执行两次
        if (event.getApplicationContext().getParent() == null) {
            initAuthorities();
        }
    }

    private void initAuthorities() {
        Map<String, Object> restControllers = applicationContext.getBeansWithAnnotation(RestController.class);
        Collection<Object> controllerInstances = restControllers.values();

        List<String> arrayList = new ArrayList<>();
        for (Object controller : controllerInstances) {
            Class<?> controllerClass = controller.getClass();
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
