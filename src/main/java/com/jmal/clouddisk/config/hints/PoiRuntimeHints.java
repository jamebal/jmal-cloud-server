package com.jmal.clouddisk.config.hints;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.HashSet;
import java.util.Set;

public class PoiRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Set<String> packages = new HashSet<>();
        packages.add("org.openxmlformats.schemas.wordprocessingml.x2006.main");
        packages.add("org.openxmlformats.schemas.wordprocessingml.x2006.main.impl");
        packages.add("org.openxmlformats.schemas.spreadsheetml.x2006.main");
        packages.add("org.openxmlformats.schemas.spreadsheetml.x2006.main.impl");
        packages.add("org.openxmlformats.schemas.presentationml.x2006.main");
        packages.add("org.openxmlformats.schemas.presentationml.x2006.main.impl");

        packages.add("org.openxmlformats.schemas.drawingml.x2006.main");
        packages.add("org.openxmlformats.schemas.drawingml.x2006.main.impl");
        packages.add("org.openxmlformats.schemas.drawingml.x2006.picture");
        packages.add("org.openxmlformats.schemas.drawingml.x2006.picture.impl");
        packages.add("org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing");
        packages.add("org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.impl");

        packages.add("org.openxmlformats.schemas.officeDocument.x2006.relationships");
        packages.add("org.openxmlformats.schemas.officeDocument.x2006.relationships.impl");
        packages.add("org.openxmlformats.schemas.officeDocument.x2006.sharedTypes");
        packages.add("org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.impl");

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        for (String basePackage : packages) {
            scanner.findCandidateComponents(basePackage).forEach(bean -> {
                String className = bean.getBeanClassName();
                if (className != null) {
                    registerAll(hints, className);
                }
            });
        }

        // 针对 .doc (HWPF) 的特殊注册 (HWPF 不使用 XMLBeans，但需要反射)
        hints.reflection().registerType(TypeReference.of("org.apache.poi.hwpf.model.StyleDescription"),
                MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_PUBLIC_METHODS);

        // 资源全量注册
        hints.resources().registerPattern("org/apache/poi/schemas/ooxml/system/ooxml/**/*");
        hints.resources().registerPattern("org/apache/xmlbeans/metadata/system/**/*");
        hints.resources().registerPattern("org/apache/poi/**/*.txt");
        hints.resources().registerPattern("org/apache/poi/**/*.properties");
    }

    private void registerAll(RuntimeHints hints, String className) {
        // 注册类本身
        hints.reflection().registerType(TypeReference.of(className),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);

        // 注册数组类型
        hints.reflection().registerType(TypeReference.of(className + "[]"),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

        // 注册内部 Factory
        hints.reflection().registerType(TypeReference.of(className + "$Factory"),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
    }
}
