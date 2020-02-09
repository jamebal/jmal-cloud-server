package com.jmal.clouddisk.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Description Map工具类
 * @Date 2019-09-06 10:28
 * @blame jmal
 */
public class MapUtils {

    /***
     * 按value值倒序排序
     * @param map
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V extends Comparable<? super V>> Map<K, V> sortReverseByValue(Map<K, V> map) {
        Map<K, V> result = new LinkedHashMap<>();

        map.entrySet().stream()
                .sorted(Map.Entry.<K, V>comparingByValue()
                        .reversed()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

//    /***
//     * 按value值升序排序
//     * @param map
//     * @param <K>
//     * @param <V>
//     * @return
//     */
//    public static <K, V extends Comparable<? super V>> Map<K, V> sortAscByValue(Map<K, V> map) {
//        Map<K, V> result = new LinkedHashMap<>();
//
//        map.entrySet().stream()
//                .sorted(Map.Entry.comparingByValue()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
//        return result;
//    }
//
//    /***
//     * 按key倒序排序
//     * @param map
//     * @param <K>
//     * @param <V>
//     * @return
//     */
//    public static <K extends Comparable<? super K>, V > Map<K, V> sortReverseByKey(Map<K, V> map) {
//        Map<K, V> result = new LinkedHashMap<>();
//
//        map.entrySet().stream()
//                .sorted(Map.Entry.<K, V>comparingByKey()
//                        .reversed()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
//        return result;
//    }
//
//    /***
//     * 按key升序排序
//     * @param map
//     * @param <K>
//     * @param <V>
//     * @return
//     */
//    public static <K extends Comparable<? super K>, V > Map<K, V> sortAscByKey(Map<K, V> map) {
//        Map<K, V> result = new LinkedHashMap<>();
//        map.entrySet().stream()
//                .sorted(Map.Entry.comparingByKey()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
//        return result;
//    }

}
