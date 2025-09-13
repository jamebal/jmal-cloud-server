package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.impl.jpa.dto.FileTagsDTO;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.FilePropsDO;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    private final FilePersistenceService filePersistenceService;

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
        removeAllByIdIn(foundIds);
        return getFileDocuments(filesToDelete);
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

    @Override
    public boolean existsByUserIdAndPathAndNameIn(String path, String userId, List<String> filenames) {
        return fileMetadataRepository.existsByUserIdAndPathAndNameIn(userId, path, filenames);
    }

    @Override
    public boolean existsByUserIdAndPathAndMd5(String userId, String path, String md5) {
        return fileMetadataRepository.existsByUserIdAndPathAndMd5(userId, path, md5);
    }

    @Override
    public void updateShareProps(FileDocument file, String shareId, ShareProperties newShareProperties, boolean isFolder) {
        ShareProperties shareProperties = filePropsRepository.findSharePropsById(file.getId());
        if (shareProperties != null) {
            shareProperties.setIsShare(newShareProperties.getIsShare());
            shareProperties.setIsPrivacy(newShareProperties.getIsPrivacy());
            shareProperties.setExtractionCode(newShareProperties.getExtractionCode());
            shareProperties.setExpiresAt(newShareProperties.getExpiresAt());
            shareProperties.setOperationPermissionList(newShareProperties.getOperationPermissionList());
        } else {
            shareProperties = newShareProperties;
        }
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(file.getPath()) + "%";
        writeService.submit(new FileOperation.UpdateShareProps(
                file.getId(),
                file.getUserId(),
                pathPrefixForLike,
                shareId,
                shareProperties,
                isFolder
        ));
    }

    @Override
    public void updateShareFirst(String fileId, boolean shareBase) {
        writeService.submit(new FileOperation.UpdateShareBaseById(fileId, shareBase));
    }

    @Override
    public void unsetShareProps(FileDocument file, boolean isFolder) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(file.getPath()) + "%";
        writeService.submit(new FileOperation.UnsetShareProps(
                file.getId(),
                file.getUserId(),
                pathPrefixForLike,
                new ShareProperties(),
                isFolder
        ));
    }

    @Override
    public void setSubShareFormShareBase(FileDocument file) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(file.getPath()) + "%";
        writeService.submit(new FileOperation.SetSubShareFormShareBase(
                file.getUserId(),
                pathPrefixForLike
        ));
    }

    @Override
    public FileDocument findByUserIdAndPathAndName(String userId, String path, String name, String... excludeFields) {
        FileMetadataDO fileMetadataDO = fileMetadataRepository.findByNameAndUserIdAndPath(name, userId, path).orElse(null);
        if (fileMetadataDO != null) {
            return fileMetadataDO.toFileDocument();
        }
        return null;
    }

    @Override
    public FileBaseDTO findFileBaseDTOByUserIdAndPathAndName(String userId, String path, String name) {
        return fileMetadataRepository.findFileBaseDTOByUserIdAndPathAndName(userId, path, name).orElse(null);
    }

    @Override
    public String findIdByUserIdAndPathAndName(String userId, String path, String name) {
        return fileMetadataRepository.findIdByUserIdAndPathAndName(userId, path, name).orElse(null);
    }

    @Override
    public long updateModifyFile(String id, long length, String md5, String suffix, String fileContentType, LocalDateTime updateTime) {
        CompletableFuture<Long> future = writeService.submit(new FileOperation.UpdateModifyFile(
                id,
                length,
                md5,
                suffix,
                fileContentType,
                updateTime
        ));
        try {
           return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<FileBaseDTO> findAllFileBaseDTOAndRemoveByIdIn(List<String> fileIds) {
        List<FileBaseDTO> filesToDelete = fileMetadataRepository.findAllFileBaseDTOByIdIn(fileIds);
        if (filesToDelete.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = filesToDelete.stream().map(FileBaseDTO::getId).toList();
        removeAllByIdIn(foundIds);
        return filesToDelete;
    }

    private void removeAllByIdIn(List<String> foundIds) {
        CompletableFuture<Void> future = writeService.submit(new FileOperation.DeleteAllByIdInBatch(foundIds));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void removeAllByFolder(FileBaseDTO fileBaseDTO) {
        String pathPrefixForLike = getPathPrefixForLike(fileBaseDTO);
        try {
            writeService.submit(new FileOperation.RemoveAllByUserIdAndPathPrefix(fileBaseDTO.getUserId(), pathPrefixForLike)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<String> findAllIdsAndRemoveByFolder(FileBaseDTO fileBaseDTO) {
        String pathPrefixForLike = getPathPrefixForLike(fileBaseDTO);
        List<String> fileIdsToDelete = fileMetadataRepository.findAllIdsByUserIdAndPathPrefix(fileBaseDTO.getUserId(), pathPrefixForLike);
        if (fileIdsToDelete.isEmpty()) {
            return List.of();
        }
        removeAllByFolder(fileBaseDTO);
        return fileIdsToDelete;
    }

    @Override
    public List<FileDocument> findAllAndRemoveByFolder(FileBaseDTO fileBaseDTO) {
        String pathPrefixForLike = getPathPrefixForLike(fileBaseDTO);
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByUserIdAndPathPrefix(fileBaseDTO.getUserId(), pathPrefixForLike);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        removeAllByFolder(fileBaseDTO);
        return getFileDocuments(fileMetadataDOList);
    }

    @Override
    public FileBaseOssPathDTO findFileBaseOssPathDTOById(String id) {
        return fileMetadataRepository.findFileBaseOssPathDTOById(id).orElse(null);
    }

    @Override
    public List<FileBaseOssPathDTO> findFileBaseOssPathDTOByIdIn(List<String> fileIds) {
        return fileMetadataRepository.findFileBaseOssPathDTOByIdIn(fileIds);
    }

    @Override
    public void removeByUserIdAndPathAndName(String userId, String path, String name) {
        try {
            writeService.submit(new FileOperation.RemoveByUserIdAndPathAndName(userId, path, name)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public long countByDelTag(int delTag) {
        return fileMetadataRepository.countByDelTag(delTag);
    }

    @Override
    public List<FileBaseDTO> findFileBaseDTOByDelTagOfLimit(int delTag, int limit) {
        return fileMetadataRepository.findFileBaseDTOByDelTagOfLimit(delTag, PageRequest.of(0, limit));
    }

    @Override
    public void removeById(String fileId) {
        try {
            writeService.submit(new FileOperation.DeleteById(fileId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public long unsetDelTag(String fileId) {
        CompletableFuture<Long> future = writeService.submit(new FileOperation.UnsetDelTag(fileId));
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void removeByIdIn(List<String> fileIds) {
        try {
            writeService.submit(new FileOperation.DeleteAllByIdInBatch(fileIds)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<FileDocument> findAllAndRemoveByIdIn(List<String> fileIds) {
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllById(fileIds);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = fileMetadataDOList.stream().map(FileMetadataDO::getId).toList();
        removeByIdIn(foundIds);
        return getFileDocuments(fileMetadataDOList);
    }

    @Override
    public FileBaseDTO findFileBaseDTOById(String fileId) {
        return fileMetadataRepository.findFileBaseDTOById(fileId).orElse(null);
    }

    @Override
    public void setIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite) {
        try {
            writeService.submit(new FileOperation.SetIsFavoriteByIdIn(fileIds, isFavorite)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void setNameAndSuffixById(String name, String suffix, String fileId) {
        try {
            writeService.submit(new FileOperation.SetNameAndSuffixById(fileId, name, suffix)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    private List<FileDocument> getFileDocuments(List<FileMetadataDO> fileMetadataDOList) {
        return fileMetadataDOList.stream().map(fileMetadataDO -> {
            FileDocument fileDocument = fileMetadataDO.toFileDocument();
            filePersistenceService.readContents(fileMetadataDO, fileDocument);
            return fileDocument;
        }).toList();
    }

    private static String getPathPrefixForLike(FileBaseDTO fileBaseDTO) {
        String path = ReUtil.escape(fileBaseDTO.getPath() + fileBaseDTO.getName() + "/");
        return MyQuery.escapeLikeSpecialChars(path) + "%";
    }
}
