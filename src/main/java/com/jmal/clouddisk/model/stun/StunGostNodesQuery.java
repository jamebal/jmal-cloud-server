package com.jmal.clouddisk.model.stun;

import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StunGostNodesQuery implements Reflective {

    private String username;

    private String password;

    private String connector;

    private String dialer;

    private String name;

    private String serverName;

    private String caFile;

    private String secure;
}
