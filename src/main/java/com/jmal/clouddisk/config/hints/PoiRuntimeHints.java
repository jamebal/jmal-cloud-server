package com.jmal.clouddisk.config.hints;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

public class PoiRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // 扫描整个根包
        String rootPackage = "org.openxmlformats.schemas";

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        // 扫描所有类
        scanner.findCandidateComponents(rootPackage).forEach(bean -> {
            String className = bean.getBeanClassName();
            if (className != null) {
                // 注册类本身
                registerWithFactory(hints, className);
            }
        });

        scanner.findCandidateComponents("org.apache.poi.schemas").forEach(bean -> {
            registerWithFactory(hints, bean.getBeanClassName());
        });

        // XMLBeans 基础实现类
        String[] baseImpls = {
                "org.apache.xmlbeans.impl.values.XmlComplexContentImpl",
                "org.apache.xmlbeans.impl.values.XmlAnyTypeImpl",
                "org.apache.xmlbeans.impl.store.Xobj",
                "com.sun.org.apache.xerces.internal.dom.DocumentImpl"
        };
        for (String impl : baseImpls) {
            hints.reflection().registerType(TypeReference.of(impl),
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }

        // 资源全量注册
        hints.resources().registerPattern("org/apache/poi/schemas/ooxml/system/ooxml/**/*");
        hints.resources().registerPattern("org/apache/xmlbeans/metadata/system/**/*");
        hints.resources().registerPattern("schemaorg_apache_xmlbeans/**/*");
        hints.resources().registerPattern("org/apache/poi/**/*.txt");
        hints.resources().registerPattern("org/apache/poi/**/*.properties");
        hints.resources().registerPattern("META-INF/services/org.apache.*");
    }

    private void registerWithFactory(RuntimeHints hints, String className) {
        // 注册当前类
        hints.reflection().registerType(TypeReference.of(className),
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);

        // XMLBeans 的每个类基本都有一个 $Factory 内部类负责实例化
        // 比如 CTPicture -> CTPicture$Factory
        hints.reflection().registerType(TypeReference.of(className + "$Factory"),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

        // 某些还有 $Enum 内部类
        hints.reflection().registerType(TypeReference.of(className + "$Enum"),
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
