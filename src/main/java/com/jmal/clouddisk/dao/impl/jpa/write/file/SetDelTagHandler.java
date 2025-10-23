package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetDelTagHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetDelTagHandler implements IDataOperationHandler<FileOperation.SetDelTag, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.SetDelTag op) {
        repo.setDelTagByUserIdAndPathPrefix(op.userId(), op.path());
        return null;
    }
}
