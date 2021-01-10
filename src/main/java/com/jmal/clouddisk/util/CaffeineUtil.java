package com.jmal.clouddisk.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * CaffeineUtil
 *
 * @author jmal
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
     * 分片写锁
     */
    private static Cache<String, Lock> chunkWriteLockCache;

    /***
     * 用户token
     */
    private static Cache<String, String> tokenCache;

    /***
     * 用户身份权限缓存
     * key: username
     * value: 权限标识列表
     */
    private final static Cache<String, List<String>> AUTHORITIES_CACHE = Caffeine.newBuilder().build();

    /***
     * 缓存userId
     * key: username
     * value: userId
     */
    private final static Cache<String, String> USER_ID_CACHE = Caffeine.newBuilder().build();

    public static List<String> getAuthoritiesCache(String username) {
        return AUTHORITIES_CACHE.getIfPresent(username);
    }

    public static void setAuthoritiesCache(String username, List<String> authorities) {
        AUTHORITIES_CACHE.put(username, authorities);
    }

    public static void removeAuthoritiesCache(String username) {
        AUTHORITIES_CACHE.invalidate(username);
    }

    public static String getUserIdCache(String username) {
        return USER_ID_CACHE.getIfPresent(username);
    }

    public static void setUserIdCache(String username, String userId) {
        USER_ID_CACHE.put(username, userId);
    }

    public static void removeUserIdCache(String username) {
        USER_ID_CACHE.invalidate(username);
    }

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
        if(chunkWriteLockCache == null){
            chunkWriteLockCache = Caffeine.newBuilder().build();
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

    public static Cache<String, Lock> getChunkWriteLockCache(){
        if(chunkWriteLockCache == null){
            initMyCache();
        }
        return chunkWriteLockCache;
    }

}
