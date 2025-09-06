package com.jmal.clouddisk.dao.impl.jpa.write;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class DataManipulationConfiguration {

    @Bean
    public Map<Class<? extends IDataOperation<?>>, IDataOperationHandler<?, ?>> operationHandlers(
            List<IDataOperationHandler<?, ?>> handlerList) {

        return handlerList.stream()
                .collect(Collectors.toMap(
                        // Key Mapper: 负责从Handler中解析出它处理的操作类型 (Class)
                        this::findOperationTypeForHandler,
                        // Value Mapper: Handler实例本身
                        Function.identity(),
                        // Merge Function: 如果有重复的Handler注册了同一个操作，保留第一个
                        (existing, _) -> existing
                ));
    }

    /**
     * 一个私有的辅助方法，使用Java反射API来安全地解析出
     * IDataOperationHandler<T, R> 中第一个泛型参数 T 的具体 Class。
     * @param handler 要解析的处理器实例
     * @return 该处理器处理的操作的Class对象
     */
    private Class<? extends IDataOperation<?>> findOperationTypeForHandler(IDataOperationHandler<?, ?> handler) {
        // 1. 遍历handler实现的所有泛型接口
        return Arrays.stream(handler.getClass().getGenericInterfaces())
                .filter(type -> type instanceof ParameterizedType)
                .map(type -> (ParameterizedType) type)
                // 2. 找到原始类型是 IDataOperationHandler 的那个接口
                .filter(pt -> pt.getRawType().equals(IDataOperationHandler.class))
                .findFirst()
                // 3. 获取它的第一个泛型参数 (T)
                .map(pt -> pt.getActualTypeArguments()[0])
                // 4. 将这个Type解析为其底层的Class
                .map(this::getRawTypeFrom)
                .orElseThrow(() -> new IllegalStateException("Could not resolve operation type for handler: " + handler.getClass().getName()));
    }

    /**
     * 从一个Type中提取其原始的Class类型。
     * @param type 要解析的Type
     * @return 对应的原始Class
     */
    @SuppressWarnings("unchecked")
    private Class<? extends IDataOperation<?>> getRawTypeFrom(Type type) {
        if (type instanceof Class<?>) {
            return (Class<? extends IDataOperation<?>>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<? extends IDataOperation<?>>) ((ParameterizedType) type).getRawType();
        }
        throw new IllegalArgumentException("Cannot resolve raw type from: " + type);
    }
}
