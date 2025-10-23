package com.jmal.clouddisk.dao.impl.jpa.write.category;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.CategoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("categoryDeleteAllByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllByIdInHandler implements IDataOperationHandler<CategoryOperation.DeleteAllByIdIn, Void> {

    private final CategoryRepository repo;

    @Override
    public Void handle(CategoryOperation.DeleteAllByIdIn op) {
        repo.deleteAllByIdIn(op.categoryIds());
        return null;
    }
}
