package com.jmal.clouddisk.office.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Action {
    private String userid;
    private com.jmal.clouddisk.office.model.enums.Action type;
}
