package com.jmal.clouddisk.dao.impl.jpa.write.category;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.CategoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.CategoryDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("categoryUpsertHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpsertHandler implements IDataOperationHandler<CategoryOperation.Upsert, Void> {

    private final CategoryRepository repo;

    @Override
    public Void handle(CategoryOperation.Upsert op) {
        CategoryDO category = op.entity();
        if (category == null) {
            return null;
        }
        if (category.getId() != null) {
            CategoryDO existingCategory = repo.findById(category.getId()).orElseThrow();
            existingCategory.updateFields(category);
            repo.save(existingCategory);
        } else {
            repo.save(category);
        }
        return null;
    }
}
