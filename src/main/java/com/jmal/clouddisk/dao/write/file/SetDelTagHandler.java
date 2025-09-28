package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
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
