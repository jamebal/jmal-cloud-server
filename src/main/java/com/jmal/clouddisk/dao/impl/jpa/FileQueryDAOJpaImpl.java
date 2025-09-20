package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFileQueryDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.TrashEntityDO;
import com.jmal.clouddisk.model.file.dto.FileBaseMountDTO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileQueryDAOJpaImpl implements IFileQueryDAO {

    private final FileMetadataRepository fileMetadataRepository;

    private final FilePersistenceService filePersistenceService;

    private final LuceneQueryService luceneQueryService;

    private final TrashRepository trashRepository;

    private final FileProperties fileProperties;

    @Override
    public Page<FileIntroVO> getFileIntroVO(UploadApiParamDTO upload) {
        Page<String> page = null;
        Pageable pageable = upload.getPageable(Sort.by(Sort.Direction.DESC, Constants.IS_FOLDER));
        SearchDTO searchDTO = SearchDTO.builder().build();
        searchDTO.setPage(upload.getPageIndex());
        searchDTO.setPageSize(upload.getPageSize());
        if (upload.getOrder() != null && upload.getSortableProp() != null) {
            searchDTO.setSortProp(upload.getSortableProp());
            searchDTO.setSortOrder(upload.getOrder());
        } else {
            searchDTO.setSortProp(Constants.FILENAME_FIELD);
            searchDTO.setSortOrder(Constants.ASCENDING);
        }
        String currentDirectory = upload.getCurrentDirectory();
        String queryFileType = upload.getQueryFileType();

        if (CharSequenceUtil.isNotBlank(queryFileType)) {
            switch (queryFileType) {
                case Constants.AUDIO,
                     Constants.DOCUMENT,
                     Constants.CONTENT_TYPE_IMAGE,
                     Constants.VIDEO -> {
                    return findAllByUserIdAndType(upload, queryFileType, pageable);
                }
                case CommonFileService.TRASH_COLLECTION_NAME -> {
                    return getTrashPage(upload);
                }
                default -> {
                    return findAllByUserIdAndPath(upload, currentDirectory, pageable);
                }
            }
        } else {
            if (currentDirectory.length() < 2) {
                Boolean isFolder = upload.getIsFolder();
                if (isFolder != null) {
                    return findAllByUserIdAndIsFolder(upload, isFolder, pageable);
                }
                if (BooleanUtil.isTrue(upload.getIsFavorite())) {
                    return findAllByUserIdAndFavorite(upload, pageable);
                }
                if (BooleanUtil.isTrue(upload.getIsMount())) {
                    page = getIdsByMountFileIdIsTrue(upload.getUserId(), pageable);
                }
                String tagId = upload.getTagId();
                if (CharSequenceUtil.isNotBlank(tagId)) {
                    page = luceneQueryService.findByUserIdAndTagId(upload.getUserId(), upload.getTagId(), searchDTO);
                }
            }
        }
        if (page == null) {
            return findAllByUserIdAndPath(upload, currentDirectory, pageable);
        }
        if (page.isEmpty()) {
            return Page.empty(pageable);
        }
        long total = page.getTotalElements();
        if (total == 0) {
            return Page.empty(pageable);
        }

        List<String> ids = page.getContent();

        List<FileIntroVO> fileIntroVOList = findAllFileIntroVOByIdIn(ids);

        return new PageImpl<>(fileIntroVOList, pageable, total);
    }

    private Page<FileIntroVO> findAllByUserIdAndType(UploadApiParamDTO upload, String queryFileType, Pageable pageable) {
        Page<FileMetadataDO> fileMetadataDOPage;
        String userId = upload.getUserId();
        switch (queryFileType) {
            case Constants.AUDIO,
                 Constants.VIDEO,
                 Constants.CONTENT_TYPE_IMAGE ->
                    fileMetadataDOPage = fileMetadataRepository.findAllByUserIdAndContentTypeStartingWith(userId, queryFileType + "%", pageable);
            case Constants.DOCUMENT ->
                    fileMetadataDOPage = fileMetadataRepository.findAllByUserIdAndSuffixIn(userId, fileProperties.getDocument(), pageable);
            default -> {
                return findAllByUserIdAndPath(upload, "/", pageable);
            }
        }
        return getFileIntroVOPage(pageable, fileMetadataDOPage);
    }

    private Page<FileIntroVO> findAllByUserIdAndFavorite(UploadApiParamDTO upload, Pageable pageable) {
        Page<FileMetadataDO> fileMetadataDOPage = fileMetadataRepository.findAllByUserIdAndIsFavoriteIsTrue(upload.getUserId(), pageable);
        return getFileIntroVOPage(pageable, fileMetadataDOPage);
    }

    private Page<FileIntroVO> findAllByUserIdAndIsFolder(UploadApiParamDTO upload, Boolean isFolder, Pageable pageable) {
        Page<FileMetadataDO> fileMetadataDOPage = fileMetadataRepository.findAllByUserIdAndIsFolder(upload.getUserId(), isFolder, pageable);
        return getFileIntroVOPage(pageable, fileMetadataDOPage);
    }

    private Page<FileIntroVO> findAllByUserIdAndPath(UploadApiParamDTO upload, String currentDirectory, Pageable pageable) {
        Page<FileMetadataDO> fileMetadataDOPage = fileMetadataRepository.findAllByUserIdAndPath(upload.getUserId(), currentDirectory, pageable);
        return getFileIntroVOPage(pageable, fileMetadataDOPage);
    }

    @NotNull
    private static PageImpl<FileIntroVO> getFileIntroVOPage(Pageable pageable, Page<FileMetadataDO> fileMetadataDOPage) {
        List<FileIntroVO> fileIntroVOList = fileMetadataDOPage.map(FileMetadataDO::toFileIntroVO).getContent();
        long now = System.currentTimeMillis();
        fileIntroVOList = fileIntroVOList.stream()
                .filter(Objects::nonNull)
                .peek(fv -> {
                    long updateMilli = TimeUntils.getMilli(fv.getUpdateDate());
                    fv.setAgoTime(now - updateMilli);
                }).toList();
        return new PageImpl<>(fileIntroVOList, pageable, fileMetadataDOPage.getTotalElements());
    }

    private Page<String> getIdsByMountFileIdIsTrue(String userId, Pageable pageable) {
        return fileMetadataRepository.findIdsByUserIdAndMountFileIdIsNotNull(userId, pageable);
    }

    private Page<FileIntroVO> getTrashPage(UploadApiParamDTO upload) {
        Pageable pageable = upload.getPageable();
        Page<TrashEntityDO> trashEntityDOPage = trashRepository.findAllByHiddenIsFalse(pageable);
        List<FileIntroVO> fileIntroVOList = trashEntityDOPage.map(TrashEntityDO::toFileIntroVO).getContent();
        return new PageImpl<>(fileIntroVOList, pageable, trashEntityDOPage.getTotalElements());
    }

    @Override
    public List<FileBaseMountDTO> getDirDocuments(UploadApiParamDTO upload) {
        return fileMetadataRepository.findAllByPathAndIsFolderIsTrue(upload.getCurrentDirectory());
    }

    @Override
    public FileDocument findBaseFileDocumentById(String id, boolean excludeContent) {
        FileMetadataDO fileMetadataDO = fileMetadataRepository.findOneById(id);
        if (fileMetadataDO == null) {
            return null;
        }
        FileDocument fileDocument = fileMetadataDO.toFileDocument();
        if (!excludeContent) {
            if (BooleanUtil.isTrue(fileMetadataDO.getHasContent())) {
                filePersistenceService.readContent(fileMetadataDO.getId(), Constants.CONTENT).ifPresent(fileDocument::setInputStream);
            }
        }
        return fileDocument;
    }

    @Override
    public List<FileIntroVO> findAllFileIntroVOByIdIn(List<String> fileIdList) {
        if (fileIdList == null || fileIdList.isEmpty()) {
            return List.of();
        }
        List<FileMetadataDO> fileMetadataDOList = fileMetadataRepository.findAllByIdIn(fileIdList);
        return sortFileIntroVOList(fileIdList, fileMetadataDOList);
    }

    private List<FileIntroVO> sortFileIntroVOList(List<String> sortIdList, List<FileMetadataDO> fileMetadataDOList) {
        long now = System.currentTimeMillis();
        Map<String, FileMetadataDO> resultMap = fileMetadataDOList.stream()
                .collect(Collectors.toMap(FileMetadataDO::getId, f -> f));
        return sortIdList.stream()
                .map(resultMap::get)
                .filter(Objects::nonNull)
                .map(fm -> {
                    FileIntroVO vo = fm.toFileIntroVO();
                    long updateMilli = TimeUntils.getMilli(vo.getUpdateDate());
                    vo.setAgoTime(now - updateMilli);
                    return vo;
                })
                .toList();
    }
}
