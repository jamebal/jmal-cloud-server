package com.jmal.clouddisk.config.hints;

import jakarta.persistence.EntityManager;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerProxy;

@Configuration
public class JpaRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // SharedEntityManager 需要的 JDK 动态代理
        hints.proxies().registerJdkProxy(EntityManager.class, EntityManagerProxy.class);
    }

}
