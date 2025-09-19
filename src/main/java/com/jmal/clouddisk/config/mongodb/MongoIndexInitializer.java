package com.jmal.clouddisk.config.mongodb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${jmalcloud.datasource.type}'=='mongodb' || '${jmalcloud.datasource.migration}'=='true'"
)
public class MongoIndexInitializer {

    // 文件路径必须与 AppRuntimeHints 中定义的一致
    private static final String DOCUMENT_CLASSES_RESOURCE_PATH = "META-INF/native/document-classes.txt";

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // 1. 从 Classpath 读取在构建时生成的文件
        List<Class<?>> entityClasses = loadClassesFromResource();

        if (entityClasses.isEmpty()) {
            log.debug("No @Document classes found in resource file '{}'. Skipping index creation.", DOCUMENT_CLASSES_RESOURCE_PATH);
            initializeMongoIndices();
            return;
        }

        log.debug("Found {} @Document entity classes from resource file for index creation.", entityClasses.size());

        // 2. 使用 MongoMappingContext 来创建解析器
        MongoMappingContext mappingContext = (MongoMappingContext) mongoTemplate.getConverter().getMappingContext();
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

        // 3. 遍历从文件中读取的类列表，创建索引
        for (Class<?> entityClass : entityClasses) {
            try {
                // 确保实体被上下文知晓（通常在第一次使用时会自动注册）
                mappingContext.getPersistentEntity(entityClass);
                IndexOperations indexOps = mongoTemplate.indexOps(entityClass);
                resolver.resolveIndexFor(entityClass).forEach(indexOps::createIndex);
                log.debug("Successfully processed indices for entity: {}", entityClass.getName());
            } catch (Exception e) {
                log.error("Error creating indices for entity class: {}", entityClass.getName(), e);
            }
        }
    }

    private List<Class<?>> loadClassesFromResource() {
        List<Class<?>> classes = new ArrayList<>();
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(DOCUMENT_CLASSES_RESOURCE_PATH)) {
            if (is == null) {
                return classes;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        try {
                            // Class.forName() 在 Native Image 中能够工作，
                            // 因为 AppRuntimeHints 已经为这些类注册了反射许可
                            classes.add(Class.forName(line.trim()));
                        } catch (ClassNotFoundException e) {
                            log.error("Class not found for name '{}' listed in resource file.", line.trim(), e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to read document classes from resource file '{}'.", DOCUMENT_CLASSES_RESOURCE_PATH, e);
        }
        return classes;
    }

    private void initializeMongoIndices() {
        // 1. 获取映射上下文
        MongoMappingContext mappingContext = (MongoMappingContext) mongoTemplate.getConverter().getMappingContext();

        // 2. 创建索引解析器
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

        // 3. 使用 Spring 的类路径扫描器自动发现所有 @Document 注解的类
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Document.class));

        // 实体类所在的包名
        String basePackage = "com.jmal.clouddisk";

        Set<BeanDefinition> beanDefinitions = provider.findCandidateComponents(basePackage);

        for (BeanDefinition beanDefinition : beanDefinitions) {
            try {
                Class<?> entityClass = Class.forName(beanDefinition.getBeanClassName());
                // 获取对应实体类的 IndexOperations
                IndexOperations indexOps = mongoTemplate.indexOps(entityClass);
                resolver.resolveIndexFor(entityClass).forEach(indexOps::createIndex);
            } catch (ClassNotFoundException e) {
                // 处理异常
                log.error("Error loading class: {}", e.getMessage(), e);
            }
        }
    }
}
