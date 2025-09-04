package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.FilePropsDO;
import com.jmal.clouddisk.model.file.ShareProperties;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileDAOJpaImpl implements IFileDAO {

    private final FileMetadataRepository fileMetadataRepository;

    private final FilePropsRepository filePropsRepository;

    private final LuceneQueryService luceneQueryService;

    @Transactional(readOnly = true)
    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        fileMetadataRepository.deleteAllByIdInBatch(userIdList);
    }

    @Override
    @Transactional(readOnly = true)
    public void updateIsPublicById(String fileId) {
        FilePropsDO props = filePropsRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("FilePropsDO not found with id: " + fileId));

        if (props.getShareProps() == null) {
            props.setShareProps(new ShareProperties());
        }
        props.getShareProps().setIsPublic(true);
    }

    @Override
    @Transactional
    public void updateTagInfoInFiles(String tagId, String newTagName, String newColor) {
        Set<String> affectedFileIds = luceneQueryService.findByTagId(tagId);
        if (affectedFileIds == null || affectedFileIds.isEmpty()) {
            return;
        }
        List<FilePropsDO> propsToUpdate = filePropsRepository.findAllById(affectedFileIds);
        for (FilePropsDO props : propsToUpdate) {
            if (props.getTags() == null) continue;
            for (Tag tag : props.getTags()) {
                if (tagId.equals(tag.getTagId())) {
                    // 更新 name 和 color
                    tag.setName(newTagName);
                    tag.setColor(newColor);
                    break;
                }
            }
        }
        filePropsRepository.saveAll(propsToUpdate);
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> findIdSubShare(String userId, String pathPrefix) {
        // 对 prefix 中的特殊字符进行转义，并将 '%' 附加到末尾
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(pathPrefix) + "%";
        return fileMetadataRepository.findIdSubShares(userId, pathPrefixForLike);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsFolderSubShare(String userId, String pathPrefix) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(pathPrefix) + "%";
        return fileMetadataRepository.existsFolderSubShare(userId, pathPrefixForLike);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsById(String id) {
        return fileMetadataRepository.existsById(id);
    }

    @Transactional
    @Override
    public void save(FileDocument file) {
        FileMetadataDO fileMetadataDO = new FileMetadataDO(file);
        fileMetadataRepository.save(fileMetadataDO);
    }

    @Transactional
    @Override
    public String upsertMountFile(FileDocument fileDocument) {
        fileDocument.setRemark("挂载 mount");
        FileMetadataDO fileMetadataDO = fileMetadataRepository.findByNameAndUserIdAndPath(fileDocument.getName(), fileDocument.getUserId(), fileDocument.getPath()).orElse(new FileMetadataDO(fileDocument));
        FileMetadataDO fileMetadata = fileMetadataRepository.save(fileMetadataDO);
        return fileMetadata.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndMountFileId(String userId, String fileId) {
        return fileMetadataRepository.existsByUserIdAndMountFileId(userId, fileId);
    }

    @Override
    @Transactional(readOnly = true)
    public String findMountFilePath(String fileId, String userId) {
        return fileMetadataRepository.findMountFilePath(fileId, userId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findByIdIn(List<String> fileIdList) {
        return fileMetadataRepository.findByIdIn(fileIdList);
    }

    @Override
    @Transactional
    public List<FileDocument> findAllAndRemoveByUserIdAndIdPrefix(String userId, String idPrefix) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(idPrefix) + "%";
        List<FileMetadataDO> filesToDelete = fileMetadataRepository.findAllByUserIdAndIdPrefix(userId, pathPrefixForLike);
        if (filesToDelete.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = filesToDelete.stream().map(FileMetadataDO::getId).toList();
        fileMetadataRepository.deleteAllByIdInBatch(foundIds);
        return filesToDelete.stream().map(FileMetadataDO::toFileDocument).toList();
    }

    @Override
    @Transactional
    public void saveAll(List<FileDocument> fileDocumentList) {
        List<FileMetadataDO> fileMetadataDOList = fileDocumentList.stream().map(FileMetadataDO::new).toList();
        fileMetadataRepository.saveAll(fileMetadataDOList);
    }

    @Override
    @Transactional
    public void removeByMountFileId(String fileId) {
        fileMetadataRepository.removeByMountFileId(fileId);
    }

    @Override
    @Transactional
    public void setSubShareByFileId(String fileId) {
        filePropsRepository.setSubShareByFileId(fileId);
    }

    @Override
    @Transactional
    public void unsetSubShareByFileId(String fileId) {
        filePropsRepository.unsetSubShareByFileId(fileId);
    }
}
