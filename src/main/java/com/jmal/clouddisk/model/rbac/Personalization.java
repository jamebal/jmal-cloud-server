package com.jmal.clouddisk.model.rbac;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Personalization {
    /**
     * 主题, auto, light, dark
     */
    private Theme theme;

    public enum Theme {
        AUTO,
        LIGHT,
        DARK;
        @JsonValue
        public String toValue() {
            return name().toLowerCase();
        }
    }

}
