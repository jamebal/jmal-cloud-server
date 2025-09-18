package com.jmal.clouddisk.dao.impl.jpa.write.etag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileEtagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("etagSetFailedEtagByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetFailedEtagByIdHandler implements IDataOperationHandler<EtagOperation.SetFailedEtagById, Void> {

    private final FileEtagRepository repo;

    @Override
    public Void handle(EtagOperation.SetFailedEtagById op) {
        if (op.needsEtagUpdate() == null) {
            repo.setFailedEtagById(op.fileId(), op.attempts(), op.errorMsg());
        } else {
            repo.setFailedEtagById(op.fileId(), op.attempts(), op.errorMsg(), op.needsEtagUpdate());
        }
        return null;
    }
}
