package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileCreateFileMetadataHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateFileMetadataHandler implements IDataOperationHandler<FileOperation.CreateFileMetadata, FileMetadataDO> {

    private final FileMetadataRepository repo;
    private final FilePersistenceService filePersistenceService;

    @Override
    public FileMetadataDO handle(FileOperation.CreateFileMetadata op) {
        FileDocument fileDocument = op.entity();
        FileMetadataDO fileMetadataDO;
        if (fileDocument.getId() != null) {
            fileMetadataDO = repo.findByPublicId(fileDocument.getId()).orElse(null);
        } else {
            fileMetadataDO = repo.findByNameAndUserIdAndPath(fileDocument.getName(), fileDocument.getUserId(), fileDocument.getPath()).orElse(null);
        }
        if (fileMetadataDO == null) {
            fileMetadataDO = new FileMetadataDO(fileDocument);
        } else {
            fileMetadataDO.updateFields(op.entity());
        }
        filePersistenceService.persistContents(fileDocument);
        return repo.save(fileMetadataDO);
    }
}
