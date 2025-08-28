package com.jmal.clouddisk.dao.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.query.QueryBaseDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class JpaQueryUtil {

    /**
     * 构建通用分页排序
     */
    public static Pageable buildPageable(QueryBaseDTO queryBaseDTO) {
        if (queryBaseDTO.getPage() != null && queryBaseDTO.getPageSize() != null) {
            int page = queryBaseDTO.getPage() - 1; // JPA页码从0开始
            int size = queryBaseDTO.getPageSize();

            // 添加排序
            if (!CharSequenceUtil.isBlank(queryBaseDTO.getSortProp()) && !CharSequenceUtil.isBlank(queryBaseDTO.getSortOrder())) {
                Sort sort = "descending".equals(queryBaseDTO.getSortOrder())
                        ? Sort.by(Sort.Direction.DESC, queryBaseDTO.getSortProp())
                        : Sort.by(Sort.Direction.ASC, queryBaseDTO.getSortProp());
                return PageRequest.of(page, size, sort);
            } else {
                return PageRequest.of(page, size);
            }
        }
        return Pageable.unpaged();
    }

}
