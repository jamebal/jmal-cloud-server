// package com.jmal.clouddisk.config.mongodb;
//
// import jakarta.persistence.metamodel.EmbeddableType;
// import jakarta.persistence.metamodel.EntityType;
// import jakarta.persistence.metamodel.ManagedType;
// import jakarta.persistence.metamodel.Metamodel;
//
// import java.util.Collections;
// import java.util.Set;
//
// /**
//  * 一个“空操作”的Metamodel实现，用于配合NoOpEntityManagerFactory。
//  */
// public class NoOpMetamodel implements Metamodel {
//     @Override
//     public <X> EntityType<X> entity(Class<X> cls) {
//         throw new IllegalArgumentException("Entity not found in No-Op Metamodel: " + cls.getName());
//     }
//     @Override
//     public <X> ManagedType<X> managedType(Class<X> cls) {
//         throw new IllegalArgumentException("Managed type not found in No-Op Metamodel: " + cls.getName());
//     }
//     @Override
//     public <X> EmbeddableType<X> embeddable(Class<X> cls) {
//         throw new IllegalArgumentException("Embeddable not found in No-Op Metamodel: " + cls.getName());
//     }
//     @Override
//     public Set<ManagedType<?>> getManagedTypes() {
//         return Collections.emptySet();
//     }
//     @Override
//     public Set<EntityType<?>> getEntities() {
//         return Collections.emptySet();
//     }
//     @Override
//     public Set<EmbeddableType<?>> getEmbeddables() {
//         return Collections.emptySet();
//     }
// }
