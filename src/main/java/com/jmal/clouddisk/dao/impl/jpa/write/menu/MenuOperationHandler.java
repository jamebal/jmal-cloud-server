package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.MenuRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class MenuOperationHandler implements IDataOperationHandler<IMenuOperation> {

    private final MenuRepository repository;

    @Override
    public void handle(IMenuOperation operation) {
        switch (operation) {
            case MenuOperation.Create createOp -> repository.save(createOp.entity());
            case MenuOperation.CreateAll createOp -> repository.saveAll(createOp.entities());
            case MenuOperation.Update updateOp -> repository.save(updateOp.entity());
            case MenuOperation.Delete deleteOp -> repository.delete(deleteOp.entity());
        }
    }
}
