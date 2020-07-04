package com.jmal.juc.rwlock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Description TODO
 * @Author jmal
 * @Date 2020-06-10 11:15
 */
public class CacheTest {
    public static Cache<String, CopyOnWriteArrayList<Integer>> writtenCache = Caffeine.newBuilder().build();

    public static void main(String[] args) {
        String key = "sfasdf";
        writtenCache.put(key,new CopyOnWriteArrayList<>());
        System.out.println(writtenCache.getIfPresent(key));
        writtenCache.invalidate(key);
        System.out.println(writtenCache.getIfPresent(key));
    }
}
