package com.jmal.clouddisk.dao.write.etag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileEtagRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("etagSetEtagByUserIdAndPathAndNameHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetEtagByUserIdAndPathAndNameHandler implements IDataOperationHandler<EtagOperation.SetEtagByUserIdAndPathAndName, Void> {

    private final FileEtagRepository repo;

    @Override
    public Void handle(EtagOperation.SetEtagByUserIdAndPathAndName op) {
        repo.setEtagByUserIdAndPathAndName(op.userId(), op.path(), op.name(), op.newEtag());
        return null;
    }
}
