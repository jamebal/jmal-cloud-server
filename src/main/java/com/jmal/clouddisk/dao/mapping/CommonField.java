package com.jmal.clouddisk.dao.mapping;

import lombok.Getter;

@Getter
public enum CommonField implements FieldMapping {
    ID("id", "_id", "id");

    private final String logical, mongo, jpa;

    CommonField(String logical, String mongo, String jpa) {
        this.logical = logical;
        this.mongo = mongo;
        this.jpa = jpa;
    }
}
