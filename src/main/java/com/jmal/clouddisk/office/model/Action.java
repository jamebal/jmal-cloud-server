package com.jmal.clouddisk.office.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Action implements Reflective {
    private String userid;
    private com.jmal.clouddisk.office.model.enums.Action type;
}
