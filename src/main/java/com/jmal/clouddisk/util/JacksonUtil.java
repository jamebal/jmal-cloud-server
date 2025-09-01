package com.jmal.clouddisk.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * Jackson JSON 工具类，用它替换 fastjson 的常用方法。
 */
public class JacksonUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 对象转 JSON 字符串
     */
    public static String toJSONString(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * 对象转 JSON 字符串（指定日期格式）
     * 等价于 fastjson 的 JSON.toJSONStringWithDateFormat
     */
    public static String toJSONStringWithDateFormat(Object obj, String dateFormat) {
        try {
            ObjectMapper customMapper = mapper.copy();
            customMapper.setDateFormat(new SimpleDateFormat(dateFormat));
            return customMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * JSON 字符串转 List
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化List失败", e);
        }
    }

    /**
     * JSON 字符串转 Map
     */
    public static <K, V> Map<K, V> parseMap(String json, Class<K> keyClass, Class<V> valueClass) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化Map失败", e);
        }
    }

    /**
     * JSON 字符串转复杂类型（如 List<Map<String, Object>>）
     */
    public static <T> T parse(String json, TypeReference<T> typeReference) {
        try {
            return mapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化复杂类型失败", e);
        }
    }
}
