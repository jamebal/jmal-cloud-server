package com.jmal.clouddisk.dao;

import lombok.Getter;

/***
 * @Description 数据源
 * @author jmal
 * @Date 2020/10/13 10:49 上午
 */
@Getter
public enum DataSourceType {
    mysql("mysql", "MySQL"),
    pgsql("pgsql", "PostgreSQL"),
    sqlite("sqlite", "sqlite"),
    jpa("jpa", "JPA"),
    mongodb("mongodb", "MongoDB");

    private final String code;
    private final String description;

    DataSourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 判断是否为关系型数据库
     */
    public boolean isRelational() {
        return this == mysql || this == sqlite || this == pgsql || this == jpa;
    }

    public static DataSourceType fromCode(String code) {
        for (DataSourceType dataSourceType : values()) {
            if (dataSourceType.code.equalsIgnoreCase(code)) {
                return dataSourceType;
            }
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + code);
    }
}
