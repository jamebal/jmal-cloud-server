package com.jmal.clouddisk.config;

import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.model.LdapConfigDO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LDAPConfig {

    @Bean
    ContextSource contextSource(IUserService userService, MongoTemplate mongoTemplate) {
        LdapContextSource contextSource = new LdapContextSource();
        LdapConfigDO ldapConfigDO = mongoTemplate.findOne(new Query(), LdapConfigDO.class);
        if (ldapConfigDO != null && BooleanUtil.isTrue(ldapConfigDO.getEnable())) {
            ConsumerDO consumerDO = userService.getUserInfoById(ldapConfigDO.getUserId());
            contextSource.setUrl("ldap://" + ldapConfigDO.getLdapServer());
            contextSource.setUserDn(ldapConfigDO.getBaseDN());
            contextSource.setPassword(UserServiceImpl.getDecryptStrByUser(ldapConfigDO.getPassword(), consumerDO));
            String[] base = ldapConfigDO.getBaseDN().split(",");
            if (base.length == 3) {
                contextSource.setBase(base[1] + "," + base[2]);
            }
        } else {
            contextSource.setUrl("ldap://localhost:389");
        }
        return contextSource;
    }


}
