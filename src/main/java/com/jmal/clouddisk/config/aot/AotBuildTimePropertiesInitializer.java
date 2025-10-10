package com.jmal.clouddisk.config.aot;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

public class AotBuildTimePropertiesInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String AOT_PROPERTIES_SOURCE_NAME = "aotBuildTimeProperties";
    private static final String AOT_PROCESSING_PROPERTY = "spring.aot.processing";
    private static final String AOT_BUILD_MODE_PROPERTY = "aot.build.mode";

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
        if (!"true".equals(System.getProperty(AOT_PROCESSING_PROPERTY))) {
            return;
        }

        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();

        if (propertySources.contains(AOT_PROPERTIES_SOURCE_NAME)) {
            return;
        }

        // 1. 从系统属性中读取我们的构建模式，默认为 "full"
        String buildMode = System.getProperty(AOT_BUILD_MODE_PROPERTY, "full");
        System.err.println("!!! AOT BUILD-TIME: Detected build mode: '" + buildMode + "'");

        // 2. 根据构建模式，动态生成AOT属性
        Map<String, Object> aotProperties = getAotProperties(buildMode);

        propertySources.addFirst(new MapPropertySource(AOT_PROPERTIES_SOURCE_NAME, aotProperties));
        System.err.println("!!! AOT BUILD-TIME: Properties injected for '" + buildMode + "' mode: " + aotProperties);
    }

    private Map<String, Object> getAotProperties(String buildMode) {
        Map<String, Object> props = new HashMap<>();

        if (buildMode.equalsIgnoreCase("mongo")) {
            props.put("jmalcloud.datasource.type", "mongodb");
            props.put("jmalcloud.datasource.jpa-enabled", false);
            props.put("jmalcloud.datasource.migration", false);
            props.put("spring.data.mongodb.uri", "dummy");
        } else {
            props.put("jmalcloud.datasource.type", "sqlite");
            props.put("jmalcloud.datasource.jpa-enabled", true);
            props.put("jmalcloud.datasource.migration", true);
            props.put("spring.datasource.url", "dummy");
            props.put("spring.data.mongodb.uri", "dummy");
        }
        return props;
    }

}
