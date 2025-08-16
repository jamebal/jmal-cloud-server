package com.jmal.clouddisk.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter; // 导入新的过滤器
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.ClassUtils;

import java.util.Objects;

import static org.springframework.aot.hint.MemberCategory.*;

public class AppRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(@NotNull RuntimeHints hints, ClassLoader classLoader) {
        // --- 注册 @Document 实体 ---
        System.out.println("Scanning for @Document entities...");
        registerByAnnotation(hints, classLoader);

        // --- 注册实现了 Reflective 接口的 DTO/VO ---
        System.out.println("Scanning for classes implementing Reflective interface...");
        registerByInterface(hints, classLoader);
    }

    private void registerByAnnotation(RuntimeHints hints, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner = createScanner();
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        findAndRegister(scanner, hints, classLoader);
    }

    private void registerByInterface(RuntimeHints hints, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner = createScanner();
        // 使用 AssignableTypeFilter 来查找实现了指定接口的类
        scanner.addIncludeFilter(new AssignableTypeFilter(Reflective.class));
        findAndRegister(scanner, hints, classLoader);
    }

    private void findAndRegister(ClassPathScanningCandidateComponentProvider scanner, RuntimeHints hints, ClassLoader classLoader) {
        String basePackage = "com.jmal.clouddisk";

        scanner.findCandidateComponents(basePackage)
                .stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(Objects::nonNull)
                .map(className -> ClassUtils.resolveClassName(className, classLoader))
                .forEach(clazz -> {
                    System.out.println("Registering reflection for: " + clazz.getName());
                    hints.reflection().registerType(clazz, values());
                });
    }

    private ClassPathScanningCandidateComponentProvider createScanner() {
        return new ClassPathScanningCandidateComponentProvider(false);
    }
}
