package com.jmal.clouddisk.config.hints;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 注册IDataOperationHandler及其泛型参数的反射信息
 */
public class OperationHandlerRuntimeHints implements RuntimeHintsRegistrar {

    private static final String BASE_PACKAGE = "com.jmal.clouddisk.dao.impl.jpa.write";

    @Override
    public void registerHints(@NotNull RuntimeHints hints, ClassLoader classLoader) {
        // findOperationHandlers().forEach(handlerClass -> {
        //     hints.reflection().registerType(handlerClass, hint ->
        //         hint.onReachableType(TypeReference.of(IDataOperationHandler.class))
        //     );
        //
        //     Arrays.stream(handlerClass.getGenericInterfaces())
        //             .filter(type -> type instanceof ParameterizedType)
        //             .map(type -> (ParameterizedType) type)
        //             .filter(pt -> {
        //                 Type rawType = pt.getRawType();
        //                 return rawType instanceof Class && IDataOperationHandler.class.isAssignableFrom((Class<?>) rawType);
        //             })
        //             .findFirst()
        //             .ifPresent(pt -> {
        //                 Type operationType = pt.getActualTypeArguments()[0];
        //                 hints.reflection().registerType(TypeReference.of((Class<?>) operationType));
        //             });
        // });
        findOperationHandlers().forEach(handlerClass -> {
            // 注册Handler本身
            hints.reflection().registerType(handlerClass);

            // 为了简单和健壮，我们直接扫描所有的IDataOperation实现
            findAllOperationClasses().forEach(opClass -> hints.reflection().registerType(opClass));
        });
    }

    private Set<Class<?>> findAllOperationClasses() {
        Set<Class<?>> optionClasses = new HashSet<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(IDataOperation.class));

        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                optionClasses.add(ClassUtils.forName(Objects.requireNonNull(bd.getBeanClassName()), null));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("Found IDataOperation classes: " + optionClasses);
        return optionClasses;
    }

    private Set<Class<?>> findOperationHandlers() {
        Set<Class<?>> handlerClasses = new HashSet<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(IDataOperationHandler.class));

        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                handlerClasses.add(ClassUtils.forName(Objects.requireNonNull(bd.getBeanClassName()), null));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("Found IDataOperationHandler classes: " + handlerClasses);
        return handlerClasses;
    }
}
