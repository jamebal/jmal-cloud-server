package com.jmal.clouddisk.aot;

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

/**
 * 这是一个利用 Reflections 库来生成AOT运行时提示文件的“测试”。
 * Reflections 库被证明在测试classpath下的扫描行为比Spring的原生扫描器更健壮。
 */
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

        // 查找所有 IDataOperationHandler 的实现类
        Set<Class<? extends IDataOperationHandler>> handlerClasses = reflections.getSubTypesOf(IDataOperationHandler.class);

        // 转换为类的全限定名字符串
        Set<String> handlerClassNames = handlerClasses.stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        // 使用断言确保找到了东西
        assertFalse(handlerClassNames.isEmpty(), "FATAL: Reflections library could not find any IDataOperationHandler implementations. The project structure or dependencies might be incorrect.");

        System.out.println("Reflections found " + handlerClassNames.size() + " IDataOperationHandler implementations:");
        handlerClassNames.forEach(name -> System.out.println("  - " + name));

        // 将类名写入目标文件
        Path outputPath = Paths.get(HINTS_FILE_PATH);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, handlerClassNames);

        System.out.println("AOT hints list successfully generated at: " + outputPath.toAbsolutePath());
    }
}
