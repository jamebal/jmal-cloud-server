package com.jmal.clouddisk.dao.impl.jpa.write.category;

import com.jmal.clouddisk.model.CategoryDO;

import java.util.Collection;

public final class CategoryOperation {
    private CategoryOperation() {}

    public record CreateAll(Iterable<CategoryDO> entities) implements ICategoryOperation<Void> {}
    public record Upsert(CategoryDO entity) implements ICategoryOperation<Void> {}
    public record UpdateSetDefaultFalseByDefaultIsTrue() implements ICategoryOperation<Void> {}
    public record UpdateSetDefaultTrueById(String categoryId) implements ICategoryOperation<Void> {}
    public record DeleteAllByIdIn(Collection<String> categoryIds) implements ICategoryOperation<Void> {}
}
