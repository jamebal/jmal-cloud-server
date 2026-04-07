package com.jmal.clouddisk.model.stun;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StunGostNodesQuery {

    private String username;

    private String password;

    private String connector;

    private String dialer;

    private String name;

    private String serverName;

    private String caFile;

    private String secure;
}
