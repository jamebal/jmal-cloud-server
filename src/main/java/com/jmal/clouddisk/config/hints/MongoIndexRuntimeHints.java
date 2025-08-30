package com.jmal.clouddisk.config.hints;

import org.jetbrains.annotations.NotNull;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.ClassUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.aot.hint.MemberCategory.*;

public class MongoIndexRuntimeHints implements RuntimeHintsRegistrar {

    // 定义要写入的资源文件的相对路径
    private static final String DOCUMENT_CLASSES_RESOURCE_PATH = "META-INF/native/document-classes.txt";

    private static final String MENU_DB_PATH = "db/menu.json";
    private static final String ROLE_DB_PATH = "db/role.json";

    @Override
    public void registerHints(@NotNull RuntimeHints hints, ClassLoader classLoader) {
        System.out.println("AOT: Scanning for @Document entities...");

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        String basePackage = "com.jmal.clouddisk";

        // 扫描并收集所有 @Document 类的全限定名
        List<String> documentClassNames = scanner.findCandidateComponents(basePackage)
                .stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(Objects::nonNull)
                .sorted() // 排序让每次生成的文件内容顺序一致
                .collect(Collectors.toList());

        // 2. 为这些类注册反射 Hint，这至关重要！
        System.out.println("AOT: Registering reflection hints for @Document classes...");
        documentClassNames.forEach(className -> {
            System.out.println("  > Registering hint for: " + className);
            hints.reflection().registerType(
                    ClassUtils.resolveClassName(className, classLoader),
                    INVOKE_DECLARED_CONSTRUCTORS, INVOKE_DECLARED_METHODS, DECLARED_FIELDS
            );
        });

        // 3. 将类名列表写入到资源文件中
        System.out.println("AOT: Writing @Document class names to resource file: " + DOCUMENT_CLASSES_RESOURCE_PATH);
        try {
            // AOT处理时，工作目录通常是项目根目录，我们需要定位到 target/classes 或 build/classes
            // 通常 Spring AOT 插件会自动处理好输出路径
            // 我们直接写入到 classloader 能找到的资源输出目录
            Path resourcesDir = Paths.get(Objects.requireNonNull(classLoader.getResource("")).toURI());
            Path outputFile = resourcesDir.resolve(DOCUMENT_CLASSES_RESOURCE_PATH);

            Files.createDirectories(outputFile.getParent());
            Files.write(outputFile, documentClassNames, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("AOT: Successfully wrote " + documentClassNames.size() + " class names.");
            hints.resources().registerPattern(DOCUMENT_CLASSES_RESOURCE_PATH);
            hints.resources().registerPattern(MENU_DB_PATH);
            hints.resources().registerPattern(ROLE_DB_PATH);
        } catch (Exception e) {
            // 在构建时抛出异常，以便立即发现问题
            throw new RuntimeException("Failed to write document classes resource file.", e);
        }
    }
}
