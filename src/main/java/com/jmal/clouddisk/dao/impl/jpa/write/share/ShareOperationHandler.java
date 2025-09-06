package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ShareOperationHandler implements IDataOperationHandler<IShareOperation> {

    private final ShareRepository shareRepository;

    @Override
    public void handle(IShareOperation operation) {
        switch (operation) {
            case ShareOperation.CreateAll createOp -> shareRepository.saveAll(createOp.entities());
        }
    }

}
