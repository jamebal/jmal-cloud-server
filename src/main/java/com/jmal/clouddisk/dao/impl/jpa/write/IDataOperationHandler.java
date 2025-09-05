package com.jmal.clouddisk.dao.impl.jpa.write;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一个处理特定类型数据操作的处理器。
 * @param <T> 它能处理的操作类型，继承自IDataOperation
 */
public interface IDataOperationHandler<T extends IDataOperation> {

    /**
     * 处理给定的操作。
     * @param operation 要处理的操作对象。
     */
    void handle(T operation);

    /**
     * **这是一个默认方法，提供了自动发现支持类型的通用实现。**
     * 它通过反射分析当前Handler实现的泛型接口，找到sealed接口类型，
     * 然后获取其所有允许的子类。
     * 子类可以选择性地覆盖此方法以提供手动列表，但通常不需要。
     * @return 一组此Handler支持的操作的Class对象。
     */
    @SuppressWarnings("unchecked")
    default Set<Class<? extends T>> supportedOperationTypes() {
        // 1. 通过反射找到当前实现类 (e.g., MenuOperationHandler) 的泛型信息
        ParameterizedType genericInterface = Arrays.stream(this.getClass().getGenericInterfaces())
                .filter(type -> type instanceof ParameterizedType)
                .map(type -> (ParameterizedType) type)
                .filter(pt -> IDataOperationHandler.class.isAssignableFrom((Class<?>) pt.getRawType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Handler must implement IDataOperationHandler with a generic type."));

        // 2. 从泛型信息中提取出操作接口的类型 (e.g., IMenuOperation.class)
        Type operationInterfaceType = genericInterface.getActualTypeArguments()[0];
        Class<T> sealedInterface = (Class<T>) operationInterfaceType;

        // 3. 防御性检查，确保这个接口是sealed的
        if (!sealedInterface.isSealed()) {
            // 如果接口不是sealed的，我们就无法自动发现，抛出清晰的错误
            throw new IllegalStateException("Automatic discovery of supported types requires the operation interface '"
                    + sealedInterface.getName() + "' to be sealed.");
        }

        // 4. 使用反射API自动获取所有允许的子类
        return Arrays.stream(sealedInterface.getPermittedSubclasses())
                .map(cls -> (Class<? extends T>) cls)
                .collect(Collectors.toUnmodifiableSet());
    }

}
