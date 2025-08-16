package com.jmal.clouddisk.office;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

@Data
public class OfficeHistory implements Reflective {
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
