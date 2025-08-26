package com.jmal.clouddisk.config.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmal.clouddisk.model.file.ExtendedProperties;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Slf4j
@Converter
public class ExtendedPropertiesConverter implements AttributeConverter<ExtendedProperties, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ExtendedProperties attribute) {
        if (attribute == null) {
            return "{}"; // 返回空JSON对象而不是null
        }
        try {
            String json = objectMapper.writeValueAsString(attribute);
            log.debug("转换ExtendedProperties为JSON: 长度={}", json.length());
            return json;
        } catch (Exception e) {
            log.error("转换ExtendedProperties对象为JSON失败: {}", e.getMessage(), e);
            return "{}"; // 返回空JSON对象作为fallback
        }
    }

    @Override
    public ExtendedProperties convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            log.debug("数据库JSON数据为空，返回新的ExtendedProperties对象");
            return new ExtendedProperties();
        }
        try {
            ExtendedProperties properties = objectMapper.readValue(dbData, ExtendedProperties.class);
            log.debug("JSON转换为ExtendedProperties成功: JSON长度={}", dbData.length());
            return properties != null ? properties : new ExtendedProperties();
        } catch (Exception e) {
            log.error("转换JSON为ExtendedProperties对象失败: dbData长度={}, error={}",
                     dbData.length(), e.getMessage(), e);
            return new ExtendedProperties(); // 返回新对象作为fallback
        }
    }
}
