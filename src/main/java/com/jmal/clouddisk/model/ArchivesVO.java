package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Description 归档展示
 * @blame jmal
 * @Date 2020/11/15 7:33 下午
 */
@Data
@NoArgsConstructor
public class ArchivesVO implements Reflective {

    /***
     * 归档id
     */
    private String id;
    /***
     * 归档名称
     */
    private String name;
    /***
     * 归档缩略名
     */
    private String slug;
    /***
     * 显示时间
     */
    private LocalDateTime date;

    private String day;

    public String date(){
        return date.format(TimeUntils.DATE_MONTH);
    }

    public String dateTime(){
        return date.format(TimeUntils.DATE_DAY);
    }

    public ArchivesVO(String id, String name, String slug, LocalDateTime date) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.date = date;
        this.day = date.format(TimeUntils.FORMAT_FILE_MONTH);
    }
}
