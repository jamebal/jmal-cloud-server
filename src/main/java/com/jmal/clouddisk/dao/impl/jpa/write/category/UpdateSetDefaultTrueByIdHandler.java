package com.jmal.clouddisk.dao.impl.jpa.write.category;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.CategoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("categoryUpdateSetDefaultTrueByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateSetDefaultTrueByIdHandler implements IDataOperationHandler<CategoryOperation.UpdateSetDefaultTrueById, Void> {

    private final CategoryRepository repo;

    @Override
    public Void handle(CategoryOperation.UpdateSetDefaultTrueById op) {
        repo.updateSetDefaultTrueById(op.categoryId());
        return null;
    }
}
