package com.jmal.clouddisk.util;

import java.math.BigDecimal;

/**
 * @Description NumberUtils
 * @Date 2019-09-02 11:11
 * @author jmal
 */
public class NumberUtils {
    /***
     * 保留两位小数
     * @param f
     * @return
     */
    public static float toFloat(float f) {
        BigDecimal b = new BigDecimal(f);
        f = b.setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
        return f;
    }

    /***
     * 保留两位小数
     * @param f
     * @return
     */
    public static double toFloat(double d) {
        BigDecimal b = new BigDecimal(d);
        d = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        return d;
    }

}
