package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpsertByUserIdAndPathAndNameHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpsertByUserIdAndPathAndNameHandler implements IDataOperationHandler<FileOperation.UpsertByUserIdAndPathAndName, String> {

    private final FileMetadataRepository repo;

    @Override
    public String handle(FileOperation.UpsertByUserIdAndPathAndName op) {
        FileMetadataDO fileMetadataDO = repo.findByNameAndUserIdAndPath(op.name(), op.userId(), op.path()).orElse(null);
        FileDocument fileDocument = op.fileDocument();
        if (fileMetadataDO != null) {
            fileMetadataDO.updateFields(fileDocument);
            repo.save(fileMetadataDO);
        } else {
            fileMetadataDO = new FileMetadataDO(fileDocument);
            repo.save(fileMetadataDO);
        }
        return null;
    }
}
