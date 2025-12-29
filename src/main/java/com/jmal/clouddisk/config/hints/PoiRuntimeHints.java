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

        packages.add("org.openxmlformats.schemas.officeDocument.x2006.main");
        packages.add("org.openxmlformats.schemas.officeDocument.x2006.main.impl");
        packages.add("org.openxmlformats.schemas.officeDocument.x2006.relationships");
        packages.add("org.openxmlformats.schemas.officeDocument.x2006.relationships.impl");

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        for (String basePackage : packages) {
            scanner.findCandidateComponents(basePackage).forEach(bean -> {
                String className = bean.getBeanClassName();
                if (className != null) {
                    registerType(hints, className);
                    // 强制注册其内部工厂 Factory
                    registerType(hints, className + "$Factory");
                    registerType(hints, className + "$Enum");
                }
            });
        }

        // 资源全量注册
        hints.resources().registerPattern("org/apache/poi/schemas/ooxml/system/ooxml/**/*");
        hints.resources().registerPattern("org/apache/xmlbeans/metadata/system/**/*");
        hints.resources().registerPattern("schemaorg_apache_xmlbeans/**/*");
        hints.resources().registerPattern("org/apache/poi/**/*.txt");
        hints.resources().registerPattern("org/apache/poi/**/*.properties");
        hints.resources().registerPattern("META-INF/services/org.apache.*");
    }

    private void registerType(RuntimeHints hints, String className) {
        hints.reflection().registerType(TypeReference.of(className),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
    }
}
