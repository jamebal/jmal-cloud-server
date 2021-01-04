package com.jmal.clouddisk.model;

import com.jmal.clouddisk.util.TimeUntils;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * @Description 归档展示
 * @blame jmal
 * @Date 2020/11/15 7:33 下午
 */
@Data
public class ArchivesVO {

    /***
     * 归档id
     */
    private String id;
    /***
     * 归档名称
     */
    private String name;
    /***
     * 显示时间
     */
    private Date date;
    /***
     * 归档缩略名
     */
    private String slug;

    public String date(){
        LocalDateTime localDateTime = date.toInstant().atZone(TimeUntils.ZONE_ID).toLocalDateTime();
        return localDateTime.format(TimeUntils.DATE_MONTH);
    }

    public String dateTime(){
        LocalDateTime localDateTime = date.toInstant().atZone(TimeUntils.ZONE_ID).toLocalDateTime();
        return localDateTime.format(TimeUntils.DATE_DAY);
    }
}
