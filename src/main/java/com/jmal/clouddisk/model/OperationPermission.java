package com.jmal.clouddisk.model;

/**
 * @author jmal
 * @Description 文件操作权限
 * @date 2023/8/10 15:19
 */
public enum OperationPermission {
    DOWNLOAD,
    UPLOAD,
    DELETE,
    PUT;

    public static OperationPermission fromString(String s) {
        if (s == null) {
            return null;
        }
        try {
            return OperationPermission.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
