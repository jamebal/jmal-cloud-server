package com.jmal.clouddisk.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.exception.ExceptionType;

import com.jmal.clouddisk.exception.CommonException;

/**
 * @Description 时间工具类
 * @Author jmal
 * @Date 2019-08-30 14:05
 * @author jmal
 */
public class TimeUntils {

    public final static String TIME_TYPE_MINUTE = "minute";
    public final static String TIME_TYPE_HOUR = "hour";
    public final static String TIME_TYPE_DAY = "day";
    public final static String TIME_TYPE_WEEK = "week";
    public final static String TIME_TYPE_MONTH = "month";
    public final static String TIME_TYPE_YEAR = "year";

    public static final DateTimeFormatter UPDATE_FORMAT_TIME = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日 HH:mm:ss");
    public static final DateTimeFormatter UPLOAD_FORMAT_TIME = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日");

    public static final DateTimeFormatter DATE_MONTH = DateTimeFormatter.ofPattern("yyyy 年 MM 月");
    public static final DateTimeFormatter DATE_DAY = DateTimeFormatter.ofPattern("MM-dd");

    private static final DateTimeFormatter FORMAT_FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMAT_FILE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FORMAT_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMAT_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter FORMAT_YEAR = DateTimeFormatter.ofPattern("yyyy");

    public static final ZoneId ZONE_ID = ZoneId.systemDefault();

    public static LocalDateTime getLocalDateTime(String time) throws CommonException {
        int timelength = time.length();
        LocalDateTime date;
        try {
            switch (timelength) {
                case 10:
                    date = LocalDate.parse(time, FORMAT_DAY).atStartOfDay();
                    break;
                case 7:
                    date = YearMonth.parse(time, FORMAT_MONTH).atDay(1).atStartOfDay();
                    break;
                case 4:
                    Year year = Year.parse(time, FORMAT_YEAR);
                    date = LocalDateTime.of(year.getValue(), 1, 1, 0, 0);
                    break;
                default:
                    date = LocalDateTime.parse(time, FORMAT_TIME);
                    break;
            }
        } catch (DateTimeParseException e) {
            throw new CommonException(ExceptionType.UNPARSEABLE_DATE.getCode(), String.format("时间格式不正确,应为%s或者%s或者%s或者%s", "yyyy", "yyyy-MM", "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss"));
        }
        return date;
    }

    /***
     * 获取该时间类型有多少个时间段
     * 例如:timeType = day,则有24个时间段
     * timeType = month,则有12个时间段
     * @param time
     * @param timeType
     */
    public static int getTrendCount(String time, String timeType) throws CommonException {
        int trendsCount;
        switch (timeType) {
            case TIME_TYPE_MINUTE:
            case TIME_TYPE_HOUR:
                // 时
                // 分
                trendsCount = 60;
                break;
            case TIME_TYPE_DAY:
                // 日
                trendsCount = 24;
                break;
            case TIME_TYPE_WEEK:
                // 周
                trendsCount = 7;
                break;
            case TIME_TYPE_MONTH:
                // 月
                trendsCount = getMaxDayOfMonth(time);
                break;
            case TIME_TYPE_YEAR:
                // 年
                trendsCount = 12;
                break;
            default:
                return throwTimeTypeException();
        }
        return trendsCount;
    }

    private static int throwTimeTypeException() throws CommonException {
        throw new CommonException(ExceptionType.UNPARSEABLE_DATE.getCode(), "时间类型格式不正确,应为:minute/hour/day/week/month/year");
    }

    /**
     * 获取某月有多少天
     *
     * @param time
     * @return
     */
    private static int getMaxDayOfMonth(String time) throws CommonException {
        if (CharSequenceUtil.isBlank(time)) {
            return 30;
        }
        LocalDateTime date = getLocalDateTime(time);
        return date.getMonth().length(isLeap(date.getYear()));
    }

    private static final long SECOND_UNIT = 1000;
    private static final long MINUTE_UNIT = SECOND_UNIT * 60;
    private static final long HOUR_UNIT = MINUTE_UNIT * 60;

    public static String getAgoTime(long now , LocalDateTime updateDate){

        int diffNumber = 0;
        String timeType = " second";

        long update = TimeUntils.getMilli(updateDate);
        long diff = now - update;
        if(diff > SECOND_UNIT && diff < MINUTE_UNIT){
            diffNumber = (int) (diff / SECOND_UNIT);
            timeType = " 秒钟";
        }else if(diff >= MINUTE_UNIT && diff < HOUR_UNIT){
            diffNumber = (int) (diff / MINUTE_UNIT);
            timeType = " 分钟";
        }else{
            return "刚刚";
        }
        return diffNumber + timeType + " 前";
    }

    /***
     * 时间戳转时间格式(yyyy-MM-dd HH:mm:ss)字符串
     * @return
     */
    public static String getStringTime(long timestamp) {
        LocalDateTime date = getLocalDateTime(timestamp);
        return date.format(FORMAT_TIME);
    }

    /***
     * yyyyMMdd
     * @return
     */
    public static String getFileTimeStrOfDay() {
        return LocalDateTime.now(TimeUntils.ZONE_ID).format(FORMAT_FILE_DAY);
    }

    /***
     * yyyy-MM
     * @return
     */
    public static String getFileTimeStrOfMonth() {
        return LocalDateTime.now(TimeUntils.ZONE_ID).format(FORMAT_FILE_MONTH);
    }

    /***
     * yyyy-MM
     * @param dateTime
     * @return
     */
    public static String getFileTimeStrOfMonth(LocalDateTime dateTime) {
        return dateTime.format(FORMAT_FILE_MONTH);
    }

    /***
     * 时间戳转LocalDateTime
     * @return
     */
    public static LocalDateTime getLocalDateTime(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        return LocalDateTime.ofInstant(instant, ZONE_ID);
    }

    public static String getStringTime(String dateType, long thisTime) {
        String time = getStringTime(thisTime);
        switch (dateType) {
            case TIME_TYPE_DAY:
                // 日
                return time;
            case TIME_TYPE_MONTH:
                // 月
                return time.substring(0, 10);
            case TIME_TYPE_YEAR:
                // 年
                return time.substring(0, 7);
            default:
                return time;
        }
    }

    /***
     * 时间戳转时间格式(yyyy-MM-dd HH:mm:ss)字符串
     * @param time
     * @return
     */
    public static long getTimestamp(String time) throws CommonException {
        LocalDateTime localDateTime = getLocalDateTime(time);
        return localDateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    public static long getMilli(LocalDateTime localDateTime){
        return localDateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    /***
     * 获取当前时间 minute/hour/day/week/month/year MIN
     * @param time
     * @param timeType
     * @return long
     * @throws CommonException
     */
    public static long toTypeThisTimeInMillis(String time, String timeType) throws CommonException {
        LocalDateTime firstDate = toTypeThisTimeLocalDateTime(time,timeType);
        return firstDate.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    /***
     * 获取当前时间 minute/hour/day/week/month/year MIN
     * @param time
     * @param timeType
     * @return LocalDateTime
     * @throws CommonException
     */
    public static LocalDateTime toTypeThisTimeLocalDateTime(String time, String timeType) throws CommonException {
        LocalDateTime firstDate = null;
        LocalDateTime date = getLocalDateTime(time);
        switch (timeType) {
            case TIME_TYPE_MINUTE:
                // 分
                firstDate = date.minusSeconds(date.getSecond()).minusNanos(date.getNano());
                break;
            case TIME_TYPE_HOUR:
                // 时
                firstDate = date.minusMinutes(date.getMinute()).minusSeconds(date.getSecond()).minusNanos(date.getNano());
                break;
            case TIME_TYPE_DAY:
                // 日
                firstDate = LocalDateTime.of(date.toLocalDate(), LocalTime.MIN);
                break;
            case TIME_TYPE_WEEK:
                // 周
                firstDate = date.with(DayOfWeek.MONDAY);
                firstDate = LocalDateTime.of(firstDate.toLocalDate(), LocalTime.MIN);
                break;
            case TIME_TYPE_MONTH:
                // 月
                firstDate = date.withDayOfMonth(1);
                firstDate = LocalDateTime.of(firstDate.toLocalDate(), LocalTime.MIN);
                break;
            case TIME_TYPE_YEAR:
                // 年
                firstDate = date.withDayOfYear(1);
                firstDate = LocalDateTime.of(firstDate.toLocalDate(), LocalTime.MIN);
                break;
            default:
                throwTimeTypeException();
        }
        return firstDate;
    }

    /***
     * 获取下一时间
     * @param time 2020-02-03 12:23:12
     * @param timeType minute/hour/day/week/month/year
     * @return
     * @throws CommonException
     */
    public static LocalDateTime toTypeNextLocalDateTime(String time, String timeType) throws CommonException {
        LocalDateTime date = getLocalDateTime(time);
        return toTypeNextLocalDateTime(date,timeType);
    }

    /***
     * 获取下一时间
     * @param date
     * @param timeType
     * @return
     * @throws CommonException
     */
    public static LocalDateTime toTypeNextLocalDateTime(LocalDateTime date, String timeType) throws CommonException {
        LocalDateTime lastDate = null;
        switch (timeType) {
            case TIME_TYPE_MINUTE:
                // 分
                lastDate = date.minusSeconds(date.getSecond()).minusNanos(date.getNano()).plusMinutes(1);
                break;
            case TIME_TYPE_HOUR:
                // 时
                lastDate = date.minusMinutes(date.getMinute()).minusSeconds(date.getSecond()).minusNanos(date.getNano()).plusHours(1);
                break;
            case TIME_TYPE_DAY:
                // 日
                lastDate = LocalDateTime.of(date.toLocalDate(), LocalTime.MAX);
                break;
            case TIME_TYPE_WEEK:
                // 周
                lastDate = date.with(DayOfWeek.SUNDAY);
                lastDate = LocalDateTime.of(lastDate.toLocalDate(), LocalTime.MAX);
                break;
            case TIME_TYPE_MONTH:
                // 月
                lastDate = date.withDayOfMonth(date.getMonth().length(isLeap(date.getYear())));
                lastDate = LocalDateTime.of(lastDate.toLocalDate(), LocalTime.MAX);
                break;
            case TIME_TYPE_YEAR:
                // 年
                lastDate = date.withDayOfYear(getMaxDayOfYear(date.getYear()));
                lastDate = LocalDateTime.of(lastDate.toLocalDate(), LocalTime.MAX);
                break;
            default:
                throwTimeTypeException();
        }
        return lastDate;
    }

    /***
     * 获取下一时间
     * @param timeType minute/hour/day/week/month/year
     * @return
     * @throws CommonException
     */
    public static LocalDateTime toNextLocalDateTime(LocalDateTime date, String timeType) throws CommonException {
        LocalDateTime lastDate = null;
        switch (timeType) {
            case TIME_TYPE_MINUTE:
                // 分
                lastDate = date.plusMinutes(1);
                break;
            case TIME_TYPE_HOUR:
                // 时
                lastDate = date.plusHours(1);
                break;
            case TIME_TYPE_DAY:
                // 日
                lastDate = date.plusDays(1);
                break;
            case TIME_TYPE_WEEK:
                // 周
                lastDate = date.plusWeeks(1);
                break;
            case TIME_TYPE_MONTH:
                // 月
                lastDate = date.plusMonths(1);
                break;
            case TIME_TYPE_YEAR:
                // 年
                lastDate = date.plusYears(1);
                break;
            default:
                throwTimeTypeException();
        }
        return lastDate;
    }

    /***
     * 获取下一时间
     * @param time 2020-02-03 12:23:12
     * @param timeType minute/hour/day/week/month/year
     * @return
     * @throws CommonException
     */
    public static long toTypeNextTimeInMillis(String time, String timeType) throws CommonException {
        LocalDateTime lastDate = toTypeNextLocalDateTime(time,timeType);
        return lastDate.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    /***
     * 判断是否为闰年
     * @param year
     * @return
     */
    private static boolean isLeap(long year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    /***
     * 判断当年有多少天
     * @param year
     * @return
     */
    private static int getMaxDayOfYear(int year) {
        return isLeap(year) ? 366 : 365;
    }

    /**
     * 获取某个分/小时/天/月的时间戳
     *
     * @param createTime 2020-02-03 12:23:12
     * @param timeType minute/hour/day/week/month/year
     * @param index
     * @return
     * @throws CommonException
     */
    public static LocalDateTime toTypeTimeOfLocalDateTime(String createTime, String timeType, int index) throws CommonException {
        LocalDateTime date = null;
        switch (timeType) {
            case TIME_TYPE_MINUTE:
                // 分
                date = tpTypeTimeOfMinute(createTime, index);
                break;
            case TIME_TYPE_HOUR:
                // 时
                date = tpTypeTimeOfHour(createTime, index);
                break;
            case TIME_TYPE_DAY:
                // 日
                date = tpTypeTimeOfDay(createTime, index);
                break;
            case TIME_TYPE_WEEK:
                // 周
                date = tpTypeTimeOfWeek(createTime, index);
                break;
            case TIME_TYPE_MONTH:
                // 月
                date = tpTypeTimeOfMonth(createTime, index);
                break;
            case TIME_TYPE_YEAR:
                // 年
                date = tpTypeTimeOfYear(createTime, index);
                break;
            default:
                throwTimeTypeException();
        }
        return date;
    }

    /***
     * 获取某个分/小时/天/月的时间戳
     * @param time
     * @param timeType
     * @param index
     * @return
     * @throws CommonException
     */
    public static long toTypeTimeOfMilli(String time, String timeType, int index) throws CommonException {
        return toTypeTimeOfLocalDateTime(time,timeType,index).atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    /**
     * 获取某个分/小时/天/月的时间 下一个时刻的时间
     * @param timeType minute/hour/day/week/month/year
     * @return
     * @throws CommonException
     */
    public static LocalDateTime toTypeTimeOfNextDate(LocalDateTime date, String timeType) throws CommonException {
        return toTypeTimeOfNextDate(date,timeType,1);
    }

    /**
     * 获取某个分/小时/天/月的时间 下一个时刻的时间
     *
     * @param timeType minute/hour/day/week/month/year
     * @return
     */
    public static LocalDateTime toTypeTimeOfNextDate(LocalDateTime date, String timeType,int amount) throws CommonException {
        switch (timeType) {
            case TIME_TYPE_MINUTE:
                // 分
                date = date.plusSeconds(amount);
                break;
            case TIME_TYPE_HOUR:
                // 时
                date = date.plusMinutes(amount);
                break;
            case TIME_TYPE_DAY:
                // 日
                date = date.plusHours(amount);
                break;
            case TIME_TYPE_WEEK:
                // 周
                date = date.plusDays(amount);
                break;
            case TIME_TYPE_MONTH:
                // 月
                date = date.plusDays(amount);
                break;
            case TIME_TYPE_YEAR:
                // 年
                date = date.plusMonths(amount);
                break;
            default:
                throwTimeTypeException();
        }
        return date;
    }

    /**
     * 获取某个分/小时/天/月的时间
     *
     * @param timeType minute/hour/day/week/month/year
     * @param index
     * @return
     */
    public static LocalDateTime toTypeTimeOfDate(String time, String timeType, int index) throws CommonException {
        return toTypeTimeOfLocalDateTime(time,timeType,index);
    }

    private static LocalDateTime tpTypeTimeOfYear(String time, int month) throws CommonException {
        LocalDateTime date = toTypeThisTimeLocalDateTime(time,TIME_TYPE_YEAR);
        return date.plusMonths(month);
    }

    private static LocalDateTime tpTypeTimeOfMonth(String time, int day) throws CommonException {
        LocalDateTime date = toTypeThisTimeLocalDateTime(time, TIME_TYPE_MONTH);
        return date.plusDays(day);
    }

    private static LocalDateTime tpTypeTimeOfWeek(String time, int day) throws CommonException {
        LocalDateTime date = toTypeThisTimeLocalDateTime(time, TIME_TYPE_WEEK);
        return date.plusDays(day);
    }

    private static LocalDateTime tpTypeTimeOfDay(String time, int hour) throws CommonException {
        LocalDateTime date = toTypeThisTimeLocalDateTime(time, TIME_TYPE_DAY);
        return date.plusHours(hour);
    }

    private static LocalDateTime tpTypeTimeOfHour(String time, int minute) throws CommonException {
        LocalDateTime date = toTypeThisTimeLocalDateTime(time, TIME_TYPE_HOUR);
        return date.plusMinutes(minute);
    }

    private static LocalDateTime tpTypeTimeOfMinute(String time, int second) throws CommonException {
        LocalDateTime date = toTypeThisTimeLocalDateTime(time, TIME_TYPE_MINUTE);
        return date.plusSeconds(second);
    }

    /***
     * 获取上一时间
     * @param timeType minute/hour/day/week/month/year
     * @return LocalDateTime
     * @throws CommonException
     */
    private static LocalDateTime toLastLocalDateTime(LocalDateTime date, String timeType) throws CommonException {
        LocalDateTime lastDate = null;
        switch (timeType) {
            case TIME_TYPE_MINUTE:
                // 分
                lastDate = date.minusSeconds(1);
                break;
            case TIME_TYPE_HOUR:
                // 时
                lastDate = date.minusMinutes(1);
                break;
            case TIME_TYPE_DAY:
                // 日
                lastDate = date.minusHours(1);
                break;
            case TIME_TYPE_WEEK:
                // 周
                lastDate = date.minusDays(1);
                break;
            case TIME_TYPE_MONTH:
                // 月
                lastDate = date.minusDays(1);
                break;
            case TIME_TYPE_YEAR:
                // 年
                lastDate = date.minusMonths(1);
                break;
            default:
                throwTimeTypeException();
        }
        return lastDate;
    }

//        public static void main(String[] args){
//        try {
//            TimeUntils.getStringTime(1580885481000L);
//            long stime = System.currentTimeMillis();
//
//            String time = "2020-01-08 14:12:45";
//            String timeType = "minute";
//
//            LocalDateTime sstime = toTypeThisTimeLocalDateTime(time,timeType);
//            Object strDate = toNextLocalDateTime(sstime,timeType);
//
//            System.out.println("耗时:"+(System.currentTimeMillis() - stime));
//
//            System.out.println(strDate);
//        } catch (CommonException e) {
//            System.err.println(e.getMsg());
//        }
//    }

    /***
     * 获取上一时间
     * @param time
     * @param timeType minute/hour/day/week/month/year
     * @return
     * @throws CommonException
     */
    public static String lastTime(String time, String timeType) throws CommonException {
        return toLastLocalDateTime(getLocalDateTime(time),timeType).format(FORMAT_TIME);
    }

    public static long toLastTimeByType(long thisTime, String timeType) throws CommonException {
        LocalDateTime date = getLocalDateTime(thisTime);
        return toLastLocalDateTime(date,timeType).atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    /***
     * 去年的该时间段 同比
     * @param time
     * @return
     * @throws CommonException
     */
    public static String lastYearOnTime(String time) throws CommonException {
        try {
            LocalDateTime date = toLastLocalDateTime(getLocalDateTime(time),TIME_TYPE_YEAR);
            date = date.minusYears(1);
            return date.format(FORMAT_TIME);
        } catch (Exception e) {
            throwTimeTypeException();
        }
        return "1997";
    }

    public static String getLocalDateTime(LocalDateTime date) {
        return date.format(FORMAT_TIME);
    }
}
