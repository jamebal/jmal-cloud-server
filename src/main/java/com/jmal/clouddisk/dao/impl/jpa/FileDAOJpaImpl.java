package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.impl.jpa.dto.FileTagsDTO;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileDAOJpaImpl implements IFileDAO {

    private final FileMetadataRepository fileMetadataRepository;

    private final ArticleRepository articleRepository;

    private final FilePropsRepository filePropsRepository;

    private final LuceneQueryService luceneQueryService;

    private final IWriteService writeService;

    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        writeService.submit(new FileOperation.DeleteAllByUserIdInBatch(userIdList));
    }

    @Override
    public void updateIsPublicById(String fileId) {
        FilePropsDO props = filePropsRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("FilePropsDO not found with id: " + fileId));

        if (props.getShareProps() == null) {
            props.setShareProps(new ShareProperties());
        }
        props.getShareProps().setIsPublic(true);
    }

    @Override
    public void updateTagInfoInFiles(String tagId, String newTagName, String newColor) {
        Set<String> affectedFileIds = luceneQueryService.findByTagId(tagId);
        if (affectedFileIds == null || affectedFileIds.isEmpty()) {
            return;
        }
        List<FileTagsDTO> fileTagsDTOList = filePropsRepository.findTagsByIdIn(affectedFileIds);

        for (FileTagsDTO dto : fileTagsDTOList) {
            if (dto.getTags() == null) continue;

            boolean tagFoundAndUpdated = false;
            for (Tag tag : dto.getTags()) {
                if (tagId.equals(tag.getTagId())) {
                    tag.setName(newTagName);
                    tag.setColor(newColor);
                    tagFoundAndUpdated = true;
                    break;
                }
            }

            if (tagFoundAndUpdated) {
                writeService.submit(new FileOperation.UpdateTagsForFile(dto.getId(), dto.getTags()));
            }
        }
    }

    @Override
    public List<String> findIdSubShare(String userId, String pathPrefix) {
        // 对 prefix 中的特殊字符进行转义，并将 '%' 附加到末尾
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(pathPrefix) + "%";
        return fileMetadataRepository.findIdSubShares(userId, pathPrefixForLike);
    }

    @Override
    public boolean existsFolderSubShare(String userId, String pathPrefix) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(pathPrefix) + "%";
        return fileMetadataRepository.existsFolderSubShare(userId, pathPrefixForLike);
    }

    @Override
    public boolean existsById(String id) {
        return fileMetadataRepository.existsById(id);
    }

    @Override
    public void save(FileDocument file) {
        FileMetadataDO fileMetadataDO = new FileMetadataDO(file);
        writeService.submit(new FileOperation.CreateFileMetadata(fileMetadataDO));
    }

    @Override
    public String upsertMountFile(FileDocument fileDocument) {
        fileDocument.setRemark("挂载 mount");
        FileMetadataDO fileMetadataDO = fileMetadataRepository.findByNameAndUserIdAndPath(fileDocument.getName(), fileDocument.getUserId(), fileDocument.getPath()).orElse(new FileMetadataDO(fileDocument));
        CompletableFuture<FileMetadataDO> future = writeService.submit(new FileOperation.CreateFileMetadata(fileMetadataDO));
        try {
            FileMetadataDO file = future.get(10, TimeUnit.SECONDS);
            return file.getId();
        } catch (Exception e) {
            log.error("upsertMountFile error", e);
        }
        return null;
    }

    @Override
    public boolean existsByUserIdAndMountFileId(String userId, String fileId) {
        return fileMetadataRepository.existsByUserIdAndMountFileId(userId, fileId);
    }

    @Override
    public String findMountFilePath(String fileId, String userId) {
        return fileMetadataRepository.findMountFilePath(fileId, userId).orElse(null);
    }

    @Override
    public List<String> findByIdIn(List<String> fileIdList) {
        return fileMetadataRepository.findByIdIn(fileIdList);
    }

    @Override
    public List<FileDocument> findAllAndRemoveByUserIdAndIdPrefix(String userId, String idPrefix) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(idPrefix) + "%";
        List<FileMetadataDO> filesToDelete = fileMetadataRepository.findAllByUserIdAndIdPrefix(userId, pathPrefixForLike);
        if (filesToDelete.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = filesToDelete.stream().map(FileMetadataDO::getId).toList();
        CompletableFuture<Void> future = writeService.submit(new FileOperation.DeleteAllByIdInBatch(foundIds));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("findAllAndRemoveByUserIdAndIdPrefix error", e);
        }
        return filesToDelete.stream().map(FileMetadataDO::toFileDocument).toList();
    }

    @Override
    public void saveAll(List<FileDocument> fileDocumentList) {
        List<FileMetadataDO> fileMetadataDOList = fileDocumentList.stream().map(FileMetadataDO::new).toList();
        writeService.submit(new FileOperation.CreateAllFileMetadata(fileMetadataDOList));
    }

    @Override
    public void removeByMountFileId(String fileId) {
        writeService.submit(new FileOperation.RemoveByMountFileId(fileId));
    }

    @Override
    public void setSubShareByFileId(String fileId) {
        writeService.submit(new FileOperation.SetShareBaseOperation(fileId));
    }

    @Override
    public void unsetSubShareByFileId(String fileId) {
        writeService.submit(new FileOperation.UnsetShareBaseOperation(fileId));
    }

    @Override
    public boolean existsByNameAndIdNotIn(String filename, String fileId) {
        return fileMetadataRepository.existsByNameAndIdNotIn(filename, Collections.singleton(fileId));
    }

    @Override
    public boolean existsBySlugAndIdNot(String slug, String fileId) {
        if (CharSequenceUtil.isBlank(slug)) {
            return articleRepository.existsBySlug(slug);
        }
        return articleRepository.existsBySlugAndIdNot(slug, fileId);
    }
}
