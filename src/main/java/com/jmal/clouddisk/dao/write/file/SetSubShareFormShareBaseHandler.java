package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetSubShareFormShareBaseHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetSubShareFormShareBaseHandler implements IDataOperationHandler<FileOperation.SetSubShareFormShareBase, Integer> {

    private final FilePropsRepository repo;

    @Override
    public Integer handle(FileOperation.SetSubShareFormShareBase op) {
        return repo.setSubShareFormShareBase(op.userId(), op.pathPrefixForLike());
    }
}
