package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 心语记录
 * @Date 2021/3/1 10:19 上午
 */
@Data
@Document(collection = "heartwings")
@CompoundIndexes({
        @CompoundIndex(name = "createTime_1", def = "{'createTime': 1}"),
})
public class HeartwingsDO {

    private String id;

    /***
     * 创建者 userId
     */
    private String creator;

    /***
     * 创建者 username
     */
    private String username;

    /***
     * 心语
     */
    private String heartwings;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
