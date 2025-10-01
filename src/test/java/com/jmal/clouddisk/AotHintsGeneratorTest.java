package com.jmal.clouddisk;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
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

    private static final String BASE_PACKAGE = "com.jmal.clouddisk.dao.impl.jpa.write";
    private static final String HINTS_FILE_PATH = "src/main/resources/META-INF/aot/handler-classes.list";

    @Test
    void generateOperationHandlerHintsWithReflections() throws IOException {
        System.out.println("Starting AOT hints generation for IDataOperationHandler (using Reflections library)...");

        // 配置 Reflections 扫描器
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackage(BASE_PACKAGE) // 扫描的根包
                .setScanners(Scanners.SubTypes) // 我们只需要找到子类型
                .setExpandSuperTypes(false) // 优化：不需要展开父类的泛型
        );

        Set<Class<? extends IDataOperationHandler>> handlerClasses = reflections.getSubTypesOf(IDataOperationHandler.class);

        Set<String> handlerClassNames = handlerClasses.stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertFalse(handlerClassNames.isEmpty(), "FATAL: Reflections library could not find any IDataOperationHandler implementations. The project structure or dependencies might be incorrect.");

        handlerClassNames.forEach(name -> System.out.println("  - " + name));

        Path outputPath = Paths.get(HINTS_FILE_PATH);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, handlerClassNames);
    }
}
