package com.jmal.clouddisk.config.aot;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * 这是一个专门用于Spring AOT构建阶段的ApplicationContextInitializer。
 * 它的作用是在AOT引擎开始分析代码之前，向环境中强行添加所有必要的“特性开关”和“触发属性”，
 * 以确保所有条件化的配置（@ConditionalOnProperty）都被AOT引擎认为是可能被激活的，
 * 从而将所有相关代码都包含在最终的Native Image中。
 * 这个初始化器只在AOT处理期间被加载，不会影响最终的运行时应用。
 */
public class AotBuildTimePropertiesInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String AOT_PROPERTIES_SOURCE_NAME = "aotBuildTimeProperties";
    private static final String AOT_PROCESSING_PROPERTY = "spring.aot.processing";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();

        // 仅在AOT处理期间启用此初始化器
        if (!"true".equals(System.getProperty(AOT_PROCESSING_PROPERTY))) {
            return;
        }

        if (propertySources.contains(AOT_PROPERTIES_SOURCE_NAME)) {
            return;
        }

        Map<String, Object> aotProperties = getAotProperties();

        // 将这些属性作为一个高优先级的PropertySource添加到环境中
        propertySources.addFirst(new MapPropertySource(AOT_PROPERTIES_SOURCE_NAME, aotProperties));

        // 打印日志，确认属性已添加（可选）
        System.out.println("AOT Build Time Properties added: " + aotProperties);
    }

    @NotNull
    private static Map<String, Object> getAotProperties() {
        Map<String, Object> aotProperties = new HashMap<>();

        // 1. 添加所有独立的“特性开关”
        aotProperties.put("jmalcloud.datasource.jpa-enabled", "true");
        aotProperties.put("jmalcloud.datasource.mongo-enabled", "true");
        aotProperties.put("jmalcloud.datasource.migration", "true");

        // 2. 添加所有用于“触发”自动配置的虚拟属性
        aotProperties.put("spring.datasource.url", "dummy");
        aotProperties.put("spring.data.mongodb.uri", "dummy");

        // 3. 添加一个用于条件判断的type属性, 或者任何一个能让条件成立的值
        aotProperties.put("jmalcloud.datasource.type", "mongodb");
        return aotProperties;
    }
}
