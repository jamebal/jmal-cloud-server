package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetNameAndSuffixByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetNameAndSuffixByIdHandler implements IDataOperationHandler<FileOperation.SetNameAndSuffixById, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.SetNameAndSuffixById op) {
        repo.setNameAndSuffixById(op.fileId(), op.name(), op.suffix());
        return null;
    }
}
