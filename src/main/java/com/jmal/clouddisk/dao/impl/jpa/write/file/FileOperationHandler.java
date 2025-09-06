package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileOperationHandler implements IDataOperationHandler<IFileOperation> {

    private final FileMetadataRepository fileMetadataRepository;
    private final FilePropsRepository filePropsRepository;
    private final ArticleRepository articleRepository;

    @Override
    public void handle(IFileOperation operation) {
        switch (operation) {
            case FileOperation.CreateFileMetadata createFileMetadata -> fileMetadataRepository.save(createFileMetadata.entity());
            case FileOperation.CreateAllFileMetadata createAllFileMetadata -> fileMetadataRepository.saveAll(createAllFileMetadata.entities());
            case FileOperation.CreateAllArticle createAllArticle -> articleRepository.saveAll(createAllArticle.entities());
            case FileOperation.SetShareBaseOperation setShareBaseOperation -> filePropsRepository.setSubShareByFileId(setShareBaseOperation.fileId());
            case FileOperation.UnsetShareBaseOperation unsetShareBaseOperation -> filePropsRepository.unsetSubShareByFileId(unsetShareBaseOperation.fileId());
            case FileOperation.DeleteById deleteById -> fileMetadataRepository.deleteById(deleteById.entity().getId());
            case FileOperation.UpdateTagsForFile updateTagsForFile -> filePropsRepository.updateTagsForFile(updateTagsForFile.fileId(), updateTagsForFile.tags());
        }
    }
}
