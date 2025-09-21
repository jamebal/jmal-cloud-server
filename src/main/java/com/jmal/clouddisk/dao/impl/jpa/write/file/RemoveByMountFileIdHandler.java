package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("fileRemoveByMountFileIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByMountFileIdHandler implements IDataOperationHandler<FileOperation.RemoveByMountFileId, Void> {

    private final FileMetadataRepository repo;
    private final FilePropsRepository filePropsRepository;

    @Override
    public Void handle(FileOperation.RemoveByMountFileId op) {
        List<Long> ids = repo.findAllIdsByMountFileId(op.fileId());
        repo.removeByMountFileId(op.fileId());
        filePropsRepository.deleteAllByIdIn(ids);
        return null;
    }
}
