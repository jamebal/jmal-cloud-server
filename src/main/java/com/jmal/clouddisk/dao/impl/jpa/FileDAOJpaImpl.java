package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.lucene.IndexStatus;
import com.jmal.clouddisk.media.TranscodeConfig;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.ShareBaseInfoDTO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.*;
import com.jmal.clouddisk.model.file.dto.*;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileDAOJpaImpl implements IFileDAO {

    private final FileMetadataRepository fileMetadataRepository;

    private final ArticleRepository articleRepository;

    private final FilePropsDAO filePropsDAO;

    private final FilePersistenceService filePersistenceService;

    private final IWriteService writeService;

    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        writeService.submit(new FileOperation.DeleteAllByUserIdInBatch(userIdList));
    }

    @Override
    public void updateIsPublicById(String fileId) {
        filePropsDAO.updateIsPublicById(fileId);
    }

    @Override
    public void updateTagInfoInFiles(String tagId, String newTagName, String newColor) {
        filePropsDAO.updateTagInfoInFiles(tagId, newTagName, newColor);
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
        return fileMetadataRepository.existsByPublicId(id);
    }

    @Override
    public String save(FileDocument file) {
        try {
            FileMetadataDO saved = writeService.submit(new FileOperation.CreateFileMetadata(file)).get(10, TimeUnit.SECONDS);
            return saved.getId();
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public String upsertMountFile(FileDocument fileDocument) {
        fileDocument.setRemark("挂载 mount");
        CompletableFuture<FileMetadataDO> future = writeService.submit(new FileOperation.CreateFileMetadata(fileDocument));
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
        return fileMetadataRepository.findByPublicIdIn(fileIdList);
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
        return getFileDocuments(filesToDelete, true);
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
        filePropsDAO.setSubShareByFileId(fileId);
    }

    @Override
    public void unsetSubShareByFileId(String fileId) {
        filePropsDAO.unsetSubShareByFileId(fileId);
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
        filePropsDAO.updateShareProps(file, shareId, newShareProperties, isFolder);
    }

    @Override
    public void updateShareFirst(String fileId, boolean shareBase) {
        filePropsDAO.updateShareFirst(fileId, shareBase);
    }

    @Override
    public void unsetShareProps(FileDocument file, boolean isFolder) {
        filePropsDAO.unsetShareProps(file, isFolder);
    }

    @Override
    public void setSubShareFormShareBase(FileDocument file) {
        filePropsDAO.setSubShareFormShareBase(file);
    }

    @Override
    public FileDocument findByUserIdAndPathAndName(String userId, String path, String name, String... includeFields) {
        FileMetadataDO fileMetadataDO = fileMetadataRepository.findByNameAndUserIdAndPath(name, userId, path).orElse(null);
        boolean readContent = false;
        for (String field : includeFields) {
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
            filePersistenceService.readContent(fileMetadataDO.getId(), Constants.CONTENT).ifPresent(fileDocument::setInputStream);
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
        CompletableFuture<Integer> future = writeService.submit(new FileOperation.UpdateModifyFile(
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
        return getFileDocuments(fileMetadataDOList, true);
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
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByIdIn(fileIds);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = fileMetadataDOList.stream().map(FileMetadataDO::getId).toList();
        removeByIdIn(foundIds);
        return getFileDocuments(fileMetadataDOList, true);
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
            FilePropsDO filePropsDO = filePropsDAO.findById(id);
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
        return getFileDocuments(fileMetadataDOList, false);
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
        filePropsDAO.removeTagsByTagIdIn(tagIds);
    }

    @Override
    public List<String> getFileIdListByTagId(String tagId) {
        return filePropsDAO.getFileIdListByTagId(tagId);
    }

    @Override
    public void setTagsByIdIn(List<String> fileIds, List<Tag> tagList) {
        filePropsDAO.setTagsByIdIn(fileIds, tagList);
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
        return filePropsDAO.getShareBaseByPath(relativePath);
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

    @Override
    public void setUpdateDateById(String fileId, LocalDateTime time) {
        try {
            writeService.submit(new FileOperation.SetUpdateDateById(fileId, time)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<FileDocument> findByPath(String path) {
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByPath(path);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        return getFileDocuments(fileMetadataDOList, false);
    }

    @Override
    public List<FileDocument> findAllAndRemoveByPathPrefix(String pathName) {
        String pathPrefixForLike = pathName + "%";
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByPathPrefix(pathPrefixForLike);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = fileMetadataDOList.stream().map(FileMetadataDO::getId).toList();
        removeByIdIn(foundIds);
        return getFileDocuments(fileMetadataDOList, true);
    }

    @Override
    public List<FileDocument> findAllAndRemoveByMountFileIdPrefix(String pathName) {
        String pathPrefixForLike = pathName + "%";
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByMountFileIdPrefix(pathPrefixForLike);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = fileMetadataDOList.stream().map(FileMetadataDO::getId).toList();
        removeByIdIn(foundIds);
        return getFileDocuments(fileMetadataDOList, true);
    }

    @Override
    public List<String> findIdsAndRemoveByIdPrefix(String pathName) {
        String pathPrefixForLike = pathName + "%";
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByIdPrefix(pathPrefixForLike);
        if (fileMetadataDOList.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = fileMetadataDOList.stream().map(FileMetadataDO::getId).toList();
        removeByIdIn(foundIds);
        return foundIds;
    }

    @Override
    public FileDocument findThumbnailContentInputStreamById(String id) {
        boolean exists = fileMetadataRepository.existsByPublicId(id);
        if (!exists) {
            return null;
        }
        FileDocument fileDocument = new FileDocument();
        filePersistenceService.readContent(id, Constants.CONTENT).ifPresent(fileDocument::setInputStream);
        return fileDocument;
    }

    @Override
    public FileBaseOperationPermissionDTO findFileBaseOperationPermissionDTOById(String fileId) {
        return fileMetadataRepository.findFileBaseOperationPermissionDTOById(fileId).orElse(null);
    }

    @Override
    public void unsetTranscodeVideo() {
        filePropsDAO.unsetTranscodeVideo();
    }

    @Override
    public long updateTranscodeVideoByIdIn(List<String> fileIdList, int status) {
        return filePropsDAO.updateTranscodeVideoByIdIn(fileIdList, status);
    }

    @Override
    public long countNotTranscodeVideo() {
        return filePropsDAO.countNotTranscodeVideo();
    }

    @Override
    public List<FileBaseDTO> findFileBaseDTOByNotTranscodeVideo() {
        return fileMetadataRepository.findFileBaseDTOByNotTranscodeVideo(0);
    }

    @Override
    public VideoInfoDO findVideoInfoById(String fileId) {
        return filePropsDAO.findVideoInfoById(fileId);
    }

    @Override
    public void setTranscodeVideoInfoByUserIdAndPathAndName(OtherProperties otherProperties, String userId, String path, String name) {
        filePropsDAO.setTranscodeVideoInfoByUserIdAndPathAndName(otherProperties, userId, path, name);
    }

    @Override
    public List<String> findTranscodeConfigIds(TranscodeConfig config) {
        return filePropsDAO.findTranscodeConfigIds(config);
    }

    @Override
    public void updateLuceneIndexStatusByIdIn(List<String> fileIdList, int indexStatus) {
        try {
            writeService.submit(new FileOperation.UpdateLuceneIndexStatusByIdIn(fileIdList, indexStatus)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public long countByLuceneIndex(int status) {
        return fileMetadataRepository.countByLuceneIndex(status);
    }

    @Override
    public List<FileBaseLuceneDTO> findFileBaseLuceneDTOByLuceneIndex(int status, int limit) {
        return fileMetadataRepository.findFileBaseLuceneDTOByLuceneIndex(status, PageRequest.of(0, limit));
    }

    @Override
    public List<FileBaseLuceneDTO> findFileBaseLuceneDTOByIdIn(List<String> fileIdList) {
        return fileMetadataRepository.findFileBaseLuceneDTOByIdIn(fileIdList);
    }

    @Override
    public void UnsetDelTagByIdIn(List<String> fileIdList) {
        try {
            writeService.submit(new FileOperation.UnsetDelTagByIdIn(fileIdList)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void setDelTag(String userId, String path) {
        try {
            writeService.submit(new FileOperation.SetDelTag(userId, path)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean existsByUnIndexed() {
        return fileMetadataRepository.existsByLuceneIndexIsLessThanEqual(IndexStatus.INDEXING.getStatus());
    }

    @Override
    public void resetIndexStatus() {
        try {
            writeService.submit(new FileOperation.ResetIndexStatus()).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public long countOssFolder() {
        return fileMetadataRepository.countByOssFolderIsNotNull();
    }

    private List<FileDocument> getFileDocuments(List<FileMetadataDO> fileMetadataDOList, boolean readContent) {
        return fileMetadataDOList.stream().map(fileMetadataDO -> {
            FileDocument fileDocument = fileMetadataDO.toFileDocument();
            if (readContent) {
                filePersistenceService.readContents(fileMetadataDO, fileDocument);
            }
            return fileDocument;
        }).toList();
    }

    private static String getPathPrefixForLike(FileBaseDTO fileBaseDTO) {
        String path = ReUtil.escape(fileBaseDTO.getPath() + fileBaseDTO.getName() + "/");
        return MyQuery.escapeLikeSpecialChars(path) + "%";
    }
}
