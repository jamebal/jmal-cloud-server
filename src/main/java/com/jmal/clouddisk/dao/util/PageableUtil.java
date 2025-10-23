package com.jmal.clouddisk.dao.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.query.QueryBaseDTO;
import com.jmal.clouddisk.service.Constants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页 DTO 转换工具类
 */
public class PageableUtil {

    /**
     * 将自定义的查询DTO转换为Spring Data的Pageable对象。
     * 此方法是通用的，适用于JPA, MongoDB等所有Spring Data模块。
     *
     * @param queryBaseDTO 包含分页和排序信息的基础DTO
     * @return Spring Data的Pageable对象
     */
    public static Pageable buildPageable(QueryBaseDTO queryBaseDTO) {
        // 检查是否存在分页参数
        if (queryBaseDTO.getPage() == null || queryBaseDTO.getPageSize() == null || queryBaseDTO.getPageSize() <= 0) {
            // 如果没有有效的分页参数，则返回一个不分页的实例
            return Pageable.unpaged();
        }

        // Pageable的页码是从0开始的，所以需要将我们习惯的第1页转换为索引0
        int page = queryBaseDTO.getPage() - 1;
        int size = queryBaseDTO.getPageSize();

        // 检查是否存在排序参数
        if (!CharSequenceUtil.isBlank(queryBaseDTO.getSortProp())) {
            // 根据排序顺序字符串决定排序方向
            Sort.Direction direction = Constants.DESCENDING.equalsIgnoreCase(queryBaseDTO.getSortOrder())
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            Sort sort;
            if (queryBaseDTO.getFirstSort() != null) {
                // 如果有优先排序条件，先应用它
                sort = queryBaseDTO.getFirstSort().and(Sort.by(direction, queryBaseDTO.getSortProp()));
            } else {
                sort = Sort.by(direction, queryBaseDTO.getSortProp());
            }
            return PageRequest.of(page, size, sort);
        } else {
            // 如果没有排序参数，则只进行分页
            return PageRequest.of(page, size);
        }
    }
}
