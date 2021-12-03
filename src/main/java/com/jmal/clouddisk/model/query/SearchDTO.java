package com.jmal.clouddisk.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description Lucene search param
 * @Date 2021/4/27 5:22 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class SearchDTO extends QueryBaseDTO {
    String keyword;
}
