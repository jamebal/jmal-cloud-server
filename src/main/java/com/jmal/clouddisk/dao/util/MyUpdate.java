package com.jmal.clouddisk.dao.util;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MyUpdate {
    // 特殊标记对象，表示 unset 操作
    public static final Object UNSET = new Object() {
        @Override
        public String toString() {
            return "<UNSET>";
        }
    };

    private final Map<String, Object> operations = new HashMap<>();

    public MyUpdate set(String logicalField, Object value) {
        operations.put(logicalField, value);
        return this;
    }

    public MyUpdate unset(String logicalField) {
        operations.put(logicalField, UNSET);
        return this;
    }

}
