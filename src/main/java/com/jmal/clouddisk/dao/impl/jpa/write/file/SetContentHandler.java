package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.FileDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetContentHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetContentHandler implements IDataOperationHandler<FileOperation.SetContent, Void> {

    private final FileMetadataRepository repo;
    private final FilePersistenceService filePersistenceService;

    @Override
    public Void handle(FileOperation.SetContent op) {
        repo.setContentById(op.id());
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(op.id());
        fileDocument.setContent(op.content());
        filePersistenceService.persistContents(fileDocument);
        return null;
    }
}
