package com.jmal.clouddisk.model;

import lombok.Data;

import java.util.List;

/**
 * @Description 分类数
 * @blame jmal
 * @Date 2020/10/27 11:43 下午
 */
@Data
public class CategoryTreeDTO {
    String value;
    String label;
    List<CategoryTreeDTO> children;
}
