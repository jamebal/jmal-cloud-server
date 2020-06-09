package com.jmal.clouddisk.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * CaffeineUtil
 *
 * @blame jmal
 */
@Component
public class CaffeineUtil {

    /***
     * 以上传的分片索引
     */
    private static Cache<String, CopyOnWriteArrayList<Integer>> resumeCache;

    /***
     * 以写入的分片索引
     */
    private static Cache<String, CopyOnWriteArrayList<Integer>> writtenCache;

    /***
     * 未写入(以上传)的分片索引
     */
    private static Cache<String, CopyOnWriteArrayList<Integer>> unWrittenCache;

    /***
     * 用户token
     */
    private static Cache<String, String> tokenCache;

    @PostConstruct
    public void initCache(){
        resumeCache = Caffeine.newBuilder().build();
        writtenCache = Caffeine.newBuilder().build();
        unWrittenCache = Caffeine.newBuilder().build();
        tokenCache = Caffeine.newBuilder().expireAfterWrite(7, TimeUnit.DAYS).build();
    }

    public static Cache<String, CopyOnWriteArrayList<Integer>> getResumeCache(){
        return resumeCache;
    }

    public static Cache<String, CopyOnWriteArrayList<Integer>> getWrittenCache(){
        return writtenCache;
    }

    public static Cache<String, CopyOnWriteArrayList<Integer>> getUnWrittenCacheCache(){
        return unWrittenCache;
    }

    public static Cache<String, String> getTokenCache(){
        return tokenCache;
    }

}
