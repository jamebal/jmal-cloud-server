package com.jmal.clouddisk.model.rbac;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Personalization {
    /**
     * 主题, auto, light, dark
     */
    private String theme;
}
