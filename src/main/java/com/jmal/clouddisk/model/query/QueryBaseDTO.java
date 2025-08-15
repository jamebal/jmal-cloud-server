package com.jmal.clouddisk.model.query;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author jmal
 * @Description 查询条件基类
 * @Date 2021/1/10 2:03 下午
 */
@Data
@Schema
public class QueryBaseDTO implements Reflective {
    @Schema(name = "page", title = "当前页")
    Integer page;
    @Schema(name = "pageSize", title = "每页条数")
    Integer pageSize;
    @Schema(name = "sortProp", title = "要排序字段")
    String sortProp;
    @Schema(name = "sortProp", title = "要排序顺序(descending|ascending)")
    String sortOrder;
}
