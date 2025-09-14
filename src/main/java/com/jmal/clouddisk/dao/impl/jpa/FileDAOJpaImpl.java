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
import com.jmal.clouddisk.model.ShareBaseInfoDTO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.*;
import com.jmal.clouddisk.model.file.dto.*;
import com.jmal.clouddisk.service.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
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

    @PersistenceContext
    private EntityManager em;

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
    public String save(FileDocument file) {
        FileMetadataDO fileMetadataDO = new FileMetadataDO(file);
        filePersistenceService.persistContents(file);
        try {
            FileMetadataDO saved = writeService.submit(new FileOperation.CreateFileMetadata(fileMetadataDO)).get(10, TimeUnit.SECONDS);
            return saved.getId();
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
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
        boolean readContent = false;
        for (String field : excludeFields) {
            if (Constants.CONTENT.equals(field)) {
                readContent = true;
                break;
            }
        }
        if (fileMetadataDO == null) {
            return null;
        }
        FileDocument fileDocument = fileMetadataDO.toFileDocument();
        if (readContent && fileMetadataDO.getHasContent()) {
            filePersistenceService.readContent(fileMetadataDO.getId(), Constants.CONTENT).ifPresent(inputStream -> {
                try (inputStream) {
                    fileDocument.setContent(inputStream.readAllBytes());
                } catch (Exception e) {
                    log.error("读取 ArticleDO contentText 失败, fileId: {}", fileMetadataDO.getId(), e);
                }
            });
        }
        return fileDocument;
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
        CompletableFuture<Integer> future = writeService.submit(new FileOperation.UnsetDelTag(fileId));
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

    @Override
    public void setContent(String id, byte[] content) {
        try {
            writeService.submit(new FileOperation.SetContent(id, content)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void setMediaCoverIsTrue(String id) {
        try {
            FilePropsDO filePropsDO = filePropsRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("FilePropsDO not found with id: " + id));
            OtherProperties otherProperties = filePropsDO.getProps();
            otherProperties.setMediaCover(true);
            writeService.submit(new FileOperation.SetMediaCoverIsTrue(id, otherProperties)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<FileBaseDTO> findAllFileBaseDTOByIdIn(List<String> fileIdList) {
        return fileMetadataRepository.findAllFileBaseDTOByIdIn(fileIdList);
    }

    @Override
    public List<FileBaseDTO> findAllByUserIdAndPathPrefix(String userId, String pathPrefix) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(pathPrefix) + "%";
        return fileMetadataRepository.findFileBaseDTOAllByUserIdAndPathPrefix(userId, pathPrefixForLike);
    }

    @Override
    public void setPathById(String id, String newFilePath) {
        try {
            writeService.submit(new FileOperation.SetPathById(id, newFilePath)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<FileDocument> findAllByUserIdAndPathAndNameIn(String userId, String toPath, List<String> fromFilenameList) {
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByUserIdAndPathAndNameIn(userId, toPath, fromFilenameList);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        return getFileDocuments(fileMetadataDOList);
    }

    @Override
    public List<String> findFilenameListByIdIn(List<String> ids) {
        return fileMetadataRepository.findFilenameListByIdIn(ids);
    }

    @Override
    public List<FileBaseAllDTO> findAllFileBaseAllDTOByUserIdAndPath(String userId, String path) {
        return fileMetadataRepository.findAllFileBaseAllDTOByUserIdAndPath(userId, path);
    }

    @Override
    public void removeTagsByTagIdIn(List<String> tagIds) {
        List<FileBaseTagsDTO> fileBaseTagsDTOList = filePropsRepository.findAllFileBaseTagsDTOByTagIdIn(tagIds);
        fileBaseTagsDTOList.forEach(fileBaseTagsDTO -> {
            Set<Tag> tags = fileBaseTagsDTO.getTags();
            tags.removeIf(tag -> tagIds.contains(tag.getTagId()));
            try {
                writeService.submit(new FileOperation.UpdateTagsForFile(fileBaseTagsDTO.getId(), tags)).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new CommonException(e.getMessage());
            }
        });
    }

    @Override
    public List<String> getFileIdListByTagId(String tagId) {
        Set<String> ids = luceneQueryService.findByTagId(tagId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().toList();
    }

    @Override
    public void setTagsByIdIn(List<String> fileIds, List<Tag> tagList) {
        try {
            Set<Tag> tagSet = Set.copyOf(tagList);
            writeService.submit(new FileOperation.UpdateTagsForFiles(fileIds, tagSet)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void setNameByMountFileId(String fileId, String newFileName) {
        try {
            writeService.submit(new FileOperation.SetNameByMountFileId(fileId, newFileName)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public ShareBaseInfoDTO getShareBaseByPath(String relativePath) {
        Path path = Paths.get(relativePath);
        if (path.getNameCount() == 0) {
            return null;
        }
        StringBuilder whereClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        StringBuilder pathStr = new StringBuilder("/");

        for (int i = 0; i < path.getNameCount(); i++) {
            if (i > 0) {
                whereClause.append(" OR ");
            }
            String filename = path.getName(i).toString();
            String pathParamName = Constants.PATH_FIELD + i;
            String nameParamName = Constants.FILENAME_FIELD + i;

            whereClause.append("(f.path = :").append(pathParamName).append(" AND f.name = :").append(nameParamName).append(")");

            params.put(pathParamName, pathStr.toString());
            params.put(nameParamName, filename);

            if (i > 0) {
                pathStr.append("/");
            }
            pathStr.append(filename);
        }

        String jpql = "SELECT new com.jmal.clouddisk.model.ShareBaseInfoDTO(" +
                "p.shareId, p.shareProps" +
                ") " +
                "FROM FileMetadataDO f JOIN f.props p " +
                "WHERE p.shareBase = true AND (" + whereClause + ")";

        TypedQuery<ShareBaseInfoDTO> query = em.createQuery(jpql, ShareBaseInfoDTO.class);
        params.forEach(query::setParameter);

        List<ShareBaseInfoDTO> results = query.getResultList();


        if (results.isEmpty()) {
            return null;
        } else {
            return results.getLast();
        }

    }

    @Override
    public void updateFileByUserIdAndPathAndName(String userId, String path, String name, UpdateFile updateFile) {
        try {
            writeService.submit(new FileOperation.UpdateFileByUserIdAndPathAndName(
                    userId,
                    path,
                    name,
                    updateFile
            )).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public String upsertByUserIdAndPathAndName(String userId, String path, String name, FileDocument fileDocument) {
        try {
            return writeService.submit(new FileOperation.UpsertByUserIdAndPathAndName(userId, path, name, fileDocument)).get(10, TimeUnit.SECONDS);
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
