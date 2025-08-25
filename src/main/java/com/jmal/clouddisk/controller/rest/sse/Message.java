package com.jmal.clouddisk.controller.rest.sse;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message {
    private String username;
    private Object body;
    private String url;
    private Long space;
}
