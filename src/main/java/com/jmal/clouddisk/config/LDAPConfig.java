package com.jmal.clouddisk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.Hashtable;

@Configuration
public class LDAPConfig {

    @Bean
    LdapContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://localhost:2");
        contextSource.setBase("dc=springframework,dc=org");
        contextSource.setPooled(true);
        contextSource.setBaseEnvironmentProperties(new Hashtable<>());
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }

}
