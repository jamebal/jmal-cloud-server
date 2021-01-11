package com.jmal.clouddisk.model.query;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Description 查询条件基类
 * @blame jmal
 * @Date 2021/1/10 2:03 下午
 */
@Data
@ApiModel
public class QueryBaseDTO {
    @ApiModelProperty(name = "page", value = "当前页")
    Integer page;
    @ApiModelProperty(name = "pageSize", value = "每页条数")
    Integer pageSize;
    @ApiModelProperty(name = "sortProp", value = "要排序字段")
    String sortProp;
    @ApiModelProperty(name = "sortProp", value = "要排序顺序(descending|ascending)")
    String sortOrder;
}
