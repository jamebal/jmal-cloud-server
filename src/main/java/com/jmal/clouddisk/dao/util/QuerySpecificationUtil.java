package com.jmal.clouddisk.dao.util;

import com.jmal.clouddisk.dao.mapping.FieldMapping;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;

public class QuerySpecificationUtil {
    public static <T> Specification<T> toSpecification(MyQuery query, FieldMapping[] fields) {
        return (root, _, cb) -> {
            Predicate predicate = cb.conjunction();
            for (var entry : query.getEqMap().entrySet()) {
                FieldMapping field = Arrays.stream(fields)
                        .filter(f -> f.getLogical().equals(entry.getKey()))
                        .findFirst().orElse(null);
                String column = field != null ? field.getJpa() : entry.getKey();
                predicate = cb.and(predicate, cb.equal(root.get(column), entry.getValue()));
            }
            return predicate;
        };
    }
}
