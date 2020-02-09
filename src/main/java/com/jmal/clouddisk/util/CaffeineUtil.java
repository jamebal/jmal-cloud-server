package com.jmal.clouddisk.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;

/**
 * CaffeineUtil
 *
 * @blame jmal
 */
public class CaffeineUtil {

    /***
     * 断点续传
     */
    private static Cache<String, List<Integer>> resumeCache;

    /***
     * 用户token
     */
    private static Cache<String, String> tokenCache;


    private CaffeineUtil(){}

    private static synchronized void syncInitResumeCache() {
        if (resumeCache == null) {
            resumeCache = Caffeine.newBuilder().build();
        }
    }

    public static Cache<String, List<Integer>> getResumeCache(){
        if(resumeCache == null){
            syncInitResumeCache();
        }
        return resumeCache;
    }

    private static synchronized void syncInitTokenCache() {
        if (tokenCache == null) {
            tokenCache = Caffeine.newBuilder().build();
        }
    }

    public static Cache<String, String> getTokenCache(){
        if(tokenCache == null){
            syncInitTokenCache();
        }
        return tokenCache;
    }

}
