package com.jmal.clouddisk.controller.rest.sse;

import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message implements Reflective {
    private String username;
    private Object body;
    private String url;
    private Long space;
}
