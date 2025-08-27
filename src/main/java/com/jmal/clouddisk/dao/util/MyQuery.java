package com.jmal.clouddisk.dao.util;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MyQuery {
    private final Map<String, Object> eqMap = new HashMap<>();
    public void eq(String field, Object value) {
        eqMap.put(field, value);
    }

}
