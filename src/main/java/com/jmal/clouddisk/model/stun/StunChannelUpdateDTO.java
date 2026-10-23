package com.jmal.clouddisk.model.stun;

import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StunChannelUpdateDTO implements Reflective {

    private String addr;
}
