package com.jmal.clouddisk.config.hints;

import org.jetbrains.annotations.NotNull;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

public class OperationHandlerRuntimeHints implements RuntimeHintsRegistrar {

    // 指向由Maven插件生成的文件
    private static final String HINTS_FILE_PATH = "META-INF/aot/handler-classes.list";

    @Override
    public void registerHints(@NotNull RuntimeHints hints, ClassLoader classLoader) {
        Resource resource = new ClassPathResource(HINTS_FILE_PATH, classLoader);
        if (!resource.exists()) {
            System.err.println("AOT HINTS WARNING: Hints file not found at '" + HINTS_FILE_PATH + "'. Skipping handler registration.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            Set<String> classNames = reader.lines().collect(Collectors.toSet());
            System.out.println("Registering hints for IDataOperationHandler classes found in file: " + classNames);

            for (String className : classNames) {
                try {
                    Class<?> handlerClass = ClassUtils.forName(className, classLoader);

                    // 为每个从文件中读取到的类，注册我们需要的反射提示
                    hints.reflection().registerType(handlerClass,
                            hint -> hint.onReachableType(com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler.class)
                    );

                } catch (ClassNotFoundException e) {
                    System.err.println("AOT HINTS WARNING: Class not found while registering hints: " + className);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read or process AOT hints file: " + HINTS_FILE_PATH, e);
        }
    }
}
