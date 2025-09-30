package com.jmal.clouddisk;

import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class AotHintsGeneratorTest {

    private static final String BASE_PACKAGE = "com.jmal.clouddisk.dao.write";
    private static final String HINTS_FILE_PATH = "src/main/resources/META-INF/aot/handler-classes.list";

    @Test
    void generateOperationHandlerHintsWithReflections() throws IOException {
        System.out.println("Starting AOT hints generation for IDataOperationHandler (using Reflections library)...");

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(BASE_PACKAGE)
                .setScanners(Scanners.SubTypes)
                .setExpandSuperTypes(false)
        );

        // 查找所有 IDataOperationHandler 的实现类
        Set<Class<? extends IDataOperationHandler>> handlerClasses = reflections.getSubTypesOf(IDataOperationHandler.class);

        // 转换为类的全限定名字符串
        Set<String> handlerClassNames = handlerClasses.stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertFalse(handlerClassNames.isEmpty(), "FATAL: Reflections library could not find any IDataOperationHandler implementations. The project structure or dependencies might be incorrect.");

        System.out.println("Reflections found " + handlerClassNames.size() + " IDataOperationHandler implementations:");
        handlerClassNames.forEach(name -> System.out.println("  - " + name));

        Path outputPath = Paths.get(HINTS_FILE_PATH);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, handlerClassNames);

        System.out.println("AOT hints list successfully generated at: " + outputPath.toAbsolutePath());
    }
}
