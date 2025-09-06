package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class TrashOperationHandler implements IDataOperationHandler<ITrashOperation> {

    private final TrashRepository trashRepository;

    @Override
    public void handle(ITrashOperation operation) {
        switch (operation) {
            case TrashOperation.CreateAll createOp -> trashRepository.saveAll(createOp.entities());
        }
    }

}
