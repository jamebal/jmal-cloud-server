package com.jmal.clouddisk.dao.write.category;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.CategoryRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
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
