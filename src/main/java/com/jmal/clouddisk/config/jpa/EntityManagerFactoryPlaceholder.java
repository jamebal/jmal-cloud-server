package com.jmal.clouddisk.config.jpa;

import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 当使用 MongoDB 作为数据源时，提供一个占位的 EntityManagerFactory Bean。
 */
@Configuration
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class EntityManagerFactoryPlaceholder {

    @Bean
    public EntityManagerFactory entityManagerFactory() {
        // 返回一个代理对象，它实现了 EntityManagerFactory 接口但不执行任何操作
        return new DummyEntityManagerFactory();
    }

    // 简单的 EntityManagerFactory 实现，所有方法返回空或默认值
    private static class DummyEntityManagerFactory implements EntityManagerFactory {
        // 实现所有必要的方法，大多数方法返回 null 或抛出 UnsupportedOperationException
        @Override
        public void close() {}

        @Override
        public Map<String, Object> getProperties() {
            return Map.of();
        }

        @Override
        public Cache getCache() {
            return null;
        }

        @Override
        public PersistenceUnitUtil getPersistenceUnitUtil() {
            return null;
        }

        @Override
        public void addNamedQuery(String s, Query query) {

        }

        @Override
        public <T> T unwrap(Class<T> aClass) {
            return null;
        }

        @Override
        public <T> void addNamedEntityGraph(String s, EntityGraph<T> entityGraph) {

        }


        // 最常用的方法
        @Override
        public jakarta.persistence.EntityManager createEntityManager() {
            throw new UnsupportedOperationException("JPA functionality is disabled");
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            return null;
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType) {
            return null;
        }

        @Override
        public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
            return null;
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            return null;
        }

        @Override
        public Metamodel getMetamodel() {
            return null;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

    }
}
