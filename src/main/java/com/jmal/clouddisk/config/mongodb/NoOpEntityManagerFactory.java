// package com.jmal.clouddisk.config.mongodb;
//
// import jakarta.persistence.*;
// import jakarta.persistence.criteria.CriteriaBuilder;
// import jakarta.persistence.metamodel.Metamodel;
//
// import java.util.Map;
//
// public class NoOpEntityManagerFactory implements EntityManagerFactory {
//
//     private static final String UNSUPPORTED_MESSAGE = "This is a No-Op EntityManagerFactory and does not support actual JPA operations.";
//
//     @Override
//     public EntityManager createEntityManager() {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public EntityManager createEntityManager(Map map) {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public EntityManager createEntityManager(SynchronizationType synchronizationType) {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public CriteriaBuilder getCriteriaBuilder() {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public Metamodel getMetamodel() {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public boolean isOpen() {
//         return false;
//     }
//
//     @Override
//     public void close() {
//
//     }
//
//     @Override
//     public Map<String, Object> getProperties() {
//         return Map.of();
//     }
//
//     @Override
//     public Cache getCache() {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public PersistenceUnitUtil getPersistenceUnitUtil() {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public void addNamedQuery(String s, Query query) {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public <T> T unwrap(Class<T> aClass) {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
//
//     @Override
//     public <T> void addNamedEntityGraph(String s, EntityGraph<T> entityGraph) {
//         throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
//     }
// }
