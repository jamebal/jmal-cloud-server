package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.media.TranscodeConfig;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.ShareBaseInfoDTO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FilePropsDO;
import com.jmal.clouddisk.model.file.OtherProperties;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.FileBaseTagsDTO;
import com.jmal.clouddisk.service.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FilePropsDAO {

    private final FilePropsRepository filePropsRepository;

    private final LuceneQueryService luceneQueryService;

    private final IWriteService writeService;

    private final DataSourceProperties dataSourceProperties;

    @PersistenceContext
    private EntityManager em;

    public FilePropsDO findById(String id) {
        return filePropsRepository.findByPublicId(id).orElseThrow(() -> new EntityNotFoundException("FilePropsDO not found with id: " + id));
    }

    public void updateIsPublicById(String fileId) {
        FilePropsDO props = findById(fileId);

        if (props.getShareProps() == null) {
            props.setShareProps(new ShareProperties());
        }
        props.getShareProps().setIsPublic(true);
        writeService.submit(new FileOperation.UpdateSharePropsById(fileId, props.getShareProps()));
    }

    public void updateTagInfoInFiles(String tagId, String newTagName, String newColor) {
        List<String> affectedFileIds = luceneQueryService.findByTagId(tagId);
        if (affectedFileIds == null || affectedFileIds.isEmpty()) {
            return;
        }
        List<FileBaseTagsDTO> fileTagsDTOList = filePropsRepository.findTagsByIdIn(affectedFileIds);

        for (FileBaseTagsDTO dto : fileTagsDTOList) {
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

    public void setSubShareByFileId(String fileId) {
        writeService.submit(new FileOperation.SetShareBaseOperation(fileId));
    }

    public void unsetSubShareByFileId(String fileId) {
        writeService.submit(new FileOperation.UnsetShareBaseOperation(fileId));
    }

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
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(ReUtil.escape(file.getPath() + file.getName() + "/")) + "%";
        writeService.submit(new FileOperation.UpdateShareProps(
                file.getId(),
                file.getUserId(),
                pathPrefixForLike,
                shareId,
                shareProperties,
                isFolder
        ));
    }

    public void updateShareFirst(String fileId, String shareId, ShareProperties shareProperties, boolean shareBase) {
        writeService.submit(new FileOperation.UpdateShareBaseById(fileId, shareId, shareProperties, shareBase));
    }

    public void unsetShareProps(FileDocument file, boolean isFolder) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(ReUtil.escape(file.getPath() + file.getName() + "/")) + "%";
        writeService.submit(new FileOperation.UnsetShareProps(
                file.getId(),
                file.getUserId(),
                pathPrefixForLike,
                new ShareProperties(),
                isFolder
        ));
    }

    public void setSubShareFormShareBase(FileDocument file) {
        String pathPrefixForLike = MyQuery.escapeLikeSpecialChars(ReUtil.escape(file.getPath() + file.getName() + "/")) + "%";
        writeService.submit(new FileOperation.SetSubShareFormShareBase(
                file.getUserId(),
                pathPrefixForLike
        ));
    }


    public void removeTagsByTagIdIn(List<String> tagIds) {
        List<String> affectedFileIds = luceneQueryService.findByTagIdIn(tagIds);
        List<FileBaseTagsDTO> fileBaseTagsDTOList = filePropsRepository.findTagsByIdIn(affectedFileIds);
        fileBaseTagsDTOList.forEach(fileBaseTagsDTO -> {
            List<Tag> tags = fileBaseTagsDTO.getTags();
            tags.removeIf(tag -> tagIds.contains(tag.getTagId()));
            try {
                writeService.submit(new FileOperation.UpdateTagsForFile(fileBaseTagsDTO.getId(), tags)).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new CommonException(e.getMessage());
            }
        });
    }

    public List<String> getFileIdListByTagId(String tagId) {
        List<String> ids = luceneQueryService.findByTagId(tagId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().toList();
    }

    public void setTagsByIdIn(List<String> fileIds, List<Tag> tagList) {
        try {
            writeService.submit(new FileOperation.UpdateTagsForFiles(fileIds, tagList)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

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

    public void unsetTranscodeVideo() {
        try {
            writeService.submit(new FileOperation.UnsetTranscodeVideo()).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    public long updateTranscodeVideoByIdIn(List<String> fileIdList, int status) {
        try {
            return writeService.submit(new FileOperation.UpdateTranscodeVideoByIdIn(fileIdList, status)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    public long countNotTranscodeVideo() {
        return filePropsRepository.countByTranscodeVideo(0);
    }


    public VideoInfoDO findVideoInfoById(String fileId) {
        return filePropsRepository.findVideoInfoById(fileId);
    }

    public void setTranscodeVideoInfoByUserIdAndPathAndName(OtherProperties otherProperties, String userId, String path, String name) {
        try {
            writeService.submit(new FileOperation.setOtherPropsByUserIdAndPathAndName(otherProperties, userId, path, name)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    public List<String> findTranscodeConfigIds(TranscodeConfig config) {
        int heightCond = config.getHeightCond();
        int bitrateCond = config.getBitrateCond();
        int frameRateCond = config.getFrameRateCond() * 1000;
        int targetHeight = config.getHeight();
        int targetBitrate = config.getBitrate();
        double targetFrameRate = config.getFrameRate();
        if (dataSourceProperties.getType() == DataSourceType.sqlite) {
            return filePropsRepository.findFileIdsToTranscodeSQLite(heightCond, bitrateCond, frameRateCond, targetHeight, targetBitrate, targetFrameRate);
        }
        if (dataSourceProperties.getType() == DataSourceType.mysql) {
            return filePropsRepository.findFileIdsToTranscodeMySQL(heightCond, bitrateCond, frameRateCond, targetHeight, targetBitrate, targetFrameRate);
        }
        if (dataSourceProperties.getType() == DataSourceType.pgsql) {
            return filePropsRepository.findFileIdsToTranscodePostgreSQL(heightCond, bitrateCond, frameRateCond, targetHeight, targetBitrate, targetFrameRate);
        }
        return List.of();
    }
}
