package com.jmal.clouddisk.config;

/**
 * 这是一个标记接口。任何实现了此接口的类，
 * 都将被自动注册以获得 GraalVM 的完整反射支持。
 * 主要用于 DTO、VO 等需要被 Spring MVC 或其他框架反射实例化的类。
 */
public interface Reflective {
    // 无需任何方法，仅作标记之用
}
