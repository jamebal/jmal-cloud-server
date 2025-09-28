package com.jmal.clouddisk.dao.write.etag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileEtagRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("etagUpdateEtagAndSizeByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateEtagAndSizeByIdHandler implements IDataOperationHandler<EtagOperation.UpdateEtagAndSizeById, Integer> {

    private final FileEtagRepository repo;

    @Override
    public Integer handle(EtagOperation.UpdateEtagAndSizeById op) {
        return repo.updateEtagAndSizeById(op.fileId(), op.etag(), op.size());
    }
}
