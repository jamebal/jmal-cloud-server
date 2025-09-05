package com.jmal.clouddisk.dao.impl.jpa.write;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class DataManipulationConfiguration {

    @Bean
    public Map<Class<? extends IDataOperation>, IDataOperationHandler<?>> operationHandlers(
            List<IDataOperationHandler<?>> handlerList) {

        return handlerList.stream()
                .flatMap(this::createEntriesForHandler) // 将复杂的逻辑提取到辅助方法中
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (h1, _) -> h1));
    }

    /**
     * 为单个Handler创建其所有支持的操作类型的映射条目。
     * @param handler 要处理的Handler
     * @return 一个包含 (操作Class -> Handler实例) 映射条目的流
     */
    private Stream<Map.Entry<Class<? extends IDataOperation>, IDataOperationHandler<?>>> createEntriesForHandler(
            IDataOperationHandler<?> handler) {

        // 1. 通过反射安全地找到Handler实现的 IDataOperationHandler<T> 泛型接口
        Type operationInterfaceType = Arrays.stream(handler.getClass().getGenericInterfaces())
                .filter(type -> type instanceof ParameterizedType)
                .map(type -> (ParameterizedType) type)
                .filter(pt -> IDataOperationHandler.class.isAssignableFrom(getRawType(pt)))
                .findFirst()
                .map(pt -> pt.getActualTypeArguments()[0])
                .orElseThrow(() -> new IllegalStateException("Handler must implement IDataOperationHandler with a generic type: " + handler.getClass()));

        // 2. 从泛型接口Type中获取其原始的Class，并断言它是一个sealed接口
        Class<?> sealedInterface = getRawType(operationInterfaceType);
        if (!sealedInterface.isSealed()) {
            throw new IllegalStateException("Operation interface " + sealedInterface.getName() + " must be sealed.");
        }

        // 3. 获取所有允许的子类，并对每一个子类进行安全的类型检查和转换
        return Arrays.stream(sealedInterface.getPermittedSubclasses())
                .map(subclass -> safelyCastToOperationClass(subclass, handler))
                .map(checkedClass -> Map.entry(checkedClass, handler));
    }

    /**
     * 安全地将一个Class<?>转换为Class<? extends IDataOperation>。
     * @param clazz 待转换的Class
     * @param handler 相关的handler，仅用于错误信息
     * @return 经过类型检查和转换后的Class
     */
    @SuppressWarnings("unchecked")
    private Class<? extends IDataOperation> safelyCastToOperationClass(Class<?> clazz, IDataOperationHandler<?> handler) {
        // 我们在运行时检查这个子类是否真的实现了IDataOperation接口。
        if (IDataOperation.class.isAssignableFrom(clazz)) {
            // 只有在检查通过后，才进行强制类型转换。
            return (Class<? extends IDataOperation>) clazz;
        } else {
            throw new IllegalStateException(
                    "The permitted subclass " + clazz.getName() +
                            " of sealed interface used by handler " + handler.getClass().getName() +
                            " does not implement IDataOperation."
            );
        }
    }

    /**
     * 从一个Type中提取其原始的Class类型 (保持不变)
     */
    private Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
            throw new IllegalArgumentException("Cannot resolve raw type from: " + type);
        }
    }
}
