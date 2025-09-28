package com.jmal.clouddisk.dao.write.category;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.CategoryRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("categoryUpdateSetDefaultFalseByDefaultIsTrueHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateSetDefaultFalseByDefaultIsTrueHandler implements IDataOperationHandler<CategoryOperation.UpdateSetDefaultFalseByDefaultIsTrue, Void> {

    private final CategoryRepository repo;

    @Override
    public Void handle(CategoryOperation.UpdateSetDefaultFalseByDefaultIsTrue op) {
        repo.updateSetDefaultFalseByDefaultIsTrue();
        return null;
    }
}
