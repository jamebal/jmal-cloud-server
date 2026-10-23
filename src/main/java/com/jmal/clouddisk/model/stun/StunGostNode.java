package com.jmal.clouddisk.model.stun;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StunGostNode implements Reflective {

    private String name;

    private String addr;

    private Connector connector;

    private Dialer dialer;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Auth {

        private String username;

        private String password;
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Connector {

        private String type;

        private Auth auth;
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Dialer {

        private String type;

        private Tls tls;

        @Getter
        @Setter
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Tls {

            private String caFile;

            private Boolean secure;

            private String serverName;
        }
    }
}
