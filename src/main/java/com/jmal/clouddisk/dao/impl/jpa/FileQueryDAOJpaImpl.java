package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Override
    public Page<FileIntroVO> getFileIntroVO(UploadApiParamDTO upload) {
        Page<String> page = null;
        Pageable pageable = upload.getPageable();
        SearchDTO searchDTO = SearchDTO.builder().build();
        searchDTO.setPage(upload.getPageIndex());
        searchDTO.setPageSize(upload.getPageSize());
        if (upload.getOrder() != null && upload.getSortableProp() != null) {
            searchDTO.setSortProp(upload.getSortableProp());
            searchDTO.setSortOrder(upload.getOrder());
        } else {
            searchDTO.setSortProp(Constants.IS_FOLDER);
            searchDTO.setSortOrder(Constants.DESCENDING);
        }
        String currentDirectory = upload.getCurrentDirectory();
        String queryFileType = upload.getQueryFileType();
        if (CharSequenceUtil.isNotBlank(queryFileType)) {
            switch (queryFileType) {
                case Constants.AUDIO,
                     Constants.DOCUMENT,
                     Constants.CONTENT_TYPE_IMAGE,
                     Constants.VIDEO ->
                        page = luceneQueryService.findByUserIdAndType(upload.getUserId(), queryFileType, searchDTO);
                case CommonFileService.TRASH_COLLECTION_NAME -> {
                    return getTrashPage(upload);
                }
                default -> page = luceneQueryService.findByUserIdAndPath(upload.getUserId(), currentDirectory, searchDTO);
            }
        } else {
            if (currentDirectory.length() < 2) {
                Boolean isFolder = upload.getIsFolder();
                if (isFolder != null) {
                    page = luceneQueryService.findByUserIdAndIsFolder(upload.getUserId(), isFolder, searchDTO);
                }
                if (BooleanUtil.isTrue(upload.getIsFavorite())) {
                    page = luceneQueryService.findByUserIdAndIsFavorite(upload.getUserId(), true, searchDTO);
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
            page = luceneQueryService.findByUserIdAndPath(upload.getUserId(), currentDirectory, searchDTO);
        }
        if (page.isEmpty()) {
            return Page.empty(pageable);
        }
        long total = page.getTotalElements();
        if (total == 0) {
            return Page.empty(pageable);
        }

        List<String> ids = page.getContent();

        List<FileIntroVO> fileIntroVOList = getFileIntroVOs(ids);

        Map<String, FileIntroVO> resultMap = fileIntroVOList.stream()
                .collect(Collectors.toMap(FileIntroVO::getId, f -> f));
        List<FileIntroVO> sorted = ids.stream()
                .map(resultMap::get)
                .filter(Objects::nonNull) // 防止有的 id 查不到
                .toList();

        long now = System.currentTimeMillis();
        sorted = sorted.parallelStream().peek(fileIntroVO -> {
            LocalDateTime updateDate = fileIntroVO.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileIntroVO.setAgoTime(now - update);
        }).toList();

        // fileIntroVOList = FileSortService.sortByFileName(upload, fileIntroVOList, upload.getOrder());
        return new PageImpl<>(sorted, pageable, total);
    }

    private List<FileIntroVO> getFileIntroVOs(List<String> ids) {
        List<FileMetadataDO> list = fileMetadataRepository.findAllByIdIn(ids);
        return list.stream().map(FileMetadataDO::toFileIntroVO).toList();
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
}
