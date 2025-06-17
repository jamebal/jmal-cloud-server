package com.jmal.clouddisk.office;

import lombok.Data;

@Data
public class OfficeHistory {
    private String created;
    private String key;
    private User user;
    private String version;

    @Data
    public static class User {
        private String id;
        private String name;
    }
}
