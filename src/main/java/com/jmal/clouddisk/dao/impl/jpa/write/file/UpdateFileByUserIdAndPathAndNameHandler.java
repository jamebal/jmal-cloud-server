package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.FilePropsDO;
import com.jmal.clouddisk.model.file.OtherProperties;
import com.jmal.clouddisk.model.file.dto.UpdateFile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateFileByUserIdAndPathAndNameHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateFileByUserIdAndPathAndNameHandler implements IDataOperationHandler<FileOperation.UpdateFileByUserIdAndPathAndName, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.UpdateFileByUserIdAndPathAndName op) {
        FileMetadataDO fileMetadataDO = repo.findByNameAndUserIdAndPath(op.name(), op.userId(), op.path()).orElse(null);
        if (fileMetadataDO != null) {
            UpdateFile file = op.updateFile();
            if (file.getContentType() != null) {
                fileMetadataDO.setContentType(file.getContentType());
            }
            if (file.getSuffix() != null) {
                fileMetadataDO.setSuffix(file.getSuffix());
            }
            if (file.getUpdateDate() != null) {
                fileMetadataDO.setUpdateDate(file.getUpdateDate());
            }
            FilePropsDO filePropsDO = fileMetadataDO.getProps();
            OtherProperties otherProperties = filePropsDO.getProps();
            if (file.getExif() != null) {
                otherProperties.setExif(file.getExif());
            }
            if (file.getVideo() != null) {
                otherProperties.setVideo(file.getVideo());
            }
            filePropsDO.setProps(otherProperties);
            fileMetadataDO.setProps(filePropsDO);
            repo.save(fileMetadataDO);
        }
        return null;
    }
}
