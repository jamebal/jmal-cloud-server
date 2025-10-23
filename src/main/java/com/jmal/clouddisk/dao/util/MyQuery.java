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

    public static String escapeLikeSpecialChars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\':
                case '%':
                case '_':
                    sb.append('\\');
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

}
