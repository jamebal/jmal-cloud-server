package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

/**
 * @author jmal
 * @Description 标签
 * @Date 2020/10/26 4:30 下午
 */
@Data
public class Tag {
    @Id
    private String id;

    private String userId;

    private String name;
    /***
     * 缩略名，默认为name
     */
    private String slug;

    String tagBackground;
}
