package com.jmal.clouddisk.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * @Description 时间工具类
 * @Author jmal
 * @Date 2019-08-30 14:05
 * @author jmal
 */
public class TimeUntils {

    public static final DateTimeFormatter UPDATE_FORMAT_TIME = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日 HH:mm:ss");
    public static final DateTimeFormatter UPLOAD_FORMAT_TIME = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日");

    public static final DateTimeFormatter DATE_MONTH = DateTimeFormatter.ofPattern("yyyy 年 MM 月");
    public static final DateTimeFormatter DATE_DAY = DateTimeFormatter.ofPattern("MM-dd");

    private static final DateTimeFormatter FORMAT_FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter FORMAT_FILE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    public static final ZoneId ZONE_ID = ZoneId.systemDefault();

    /***
     * yyyyMMdd
     */
    public static String getFileTimeStrOfDay() {
        return LocalDateTime.now(TimeUntils.ZONE_ID).format(FORMAT_FILE_DAY);
    }

    /***
     * yyyy-MM
     */
    public static String getFileTimeStrOfMonth() {
        return LocalDateTime.now(TimeUntils.ZONE_ID).format(FORMAT_FILE_MONTH);
    }

    /***
     * yyyy-MM
     */
    public static String getFileTimeStrOfMonth(LocalDateTime dateTime) {
        return dateTime.format(FORMAT_FILE_MONTH);
    }

    /***
     * 时间戳转LocalDateTime
     */
    public static LocalDateTime getLocalDateTime(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        return LocalDateTime.ofInstant(instant, ZONE_ID);
    }

    public static long getMilli(LocalDateTime localDateTime){
        return localDateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    public static boolean isWithinOneSecond(LocalDateTime time1, LocalDateTime time2) {
        long secondsDifference = Math.abs(time1.until(time2, ChronoUnit.SECONDS));
        return secondsDifference < 1;
    }
}
