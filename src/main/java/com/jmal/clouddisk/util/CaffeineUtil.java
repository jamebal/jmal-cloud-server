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
        initMyCache();
    }

    public static void initMyCache(){
        if(resumeCache == null){
            resumeCache = Caffeine.newBuilder().build();
        }
        if(writtenCache == null){
            writtenCache = Caffeine.newBuilder().build();
        }
        if(unWrittenCache == null){
            unWrittenCache = Caffeine.newBuilder().build();
        }
        if(tokenCache == null){
            tokenCache = Caffeine.newBuilder().expireAfterWrite(7, TimeUnit.DAYS).build();
        }
    }

    public static Cache<String, CopyOnWriteArrayList<Integer>> getResumeCache(){
        if(resumeCache == null){
            initMyCache();
        }
        return resumeCache;
    }

    public static Cache<String, CopyOnWriteArrayList<Integer>> getWrittenCache(){
        if(writtenCache == null){
            initMyCache();
        }
        return writtenCache;
    }

    public static Cache<String, CopyOnWriteArrayList<Integer>> getUnWrittenCacheCache(){
        if(unWrittenCache == null){
            initMyCache();
        }
        return unWrittenCache;
    }


    public static Cache<String, String> getTokenCache(){
        if(tokenCache == null){
            initMyCache();
        }
        return tokenCache;
    }

}
