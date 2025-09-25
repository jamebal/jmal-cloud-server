package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FilePropsDO;
import com.jmal.clouddisk.model.file.OtherProperties;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.FileBaseTagsDTO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FilePropsRepository extends JpaRepository<FilePropsDO, Long> , JpaSpecificationExecutor<FilePropsDO> {

    @Query("UPDATE FilePropsDO p SET p.shareBase = true WHERE p.publicId = :fileId")
    @Modifying
    void setSubShareByFileId(String fileId);

    @Query("UPDATE FilePropsDO p SET p.shareBase = null WHERE p.publicId = :fileId")
    @Modifying
    void unsetSubShareByFileId(String fileId);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseTagsDTO(p.publicId, p.tags) FROM FilePropsDO p WHERE p.publicId IN :fileIds")
    List<FileBaseTagsDTO> findTagsByIdIn(@Param("fileIds") Collection<String> fileIds);

    /**
     * 只更新tags字段
     */
    @Modifying
    @Query("UPDATE FilePropsDO p SET p.tags = :tags WHERE p.publicId = :id")
    void updateTagsForFile(@Param("id") String fileId, @Param("tags") List<Tag> tags);

    @Modifying
    @Query("UPDATE FilePropsDO p SET p.tags = :tags WHERE p.publicId IN :fileIds")
    void updateTagsForFiles(@Param("fileIds") List<String> fileIds, @Param("tags") List<Tag> tags);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = :shareId, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.primaryId IN (" +
            "    SELECT fm.props.primaryId FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId AND fm.path LIKE :pathPrefix ESCAPE '\\'" +
            ")")
    int updateFolderShareProps(
            @Param("userId") String userId,
            @Param("pathPrefix") String pathPrefix,
            @Param("shareId") String shareId,
            @Param("shareProps") ShareProperties shareProps);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = :shareId, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.primaryId IN (" +
            "    SELECT fm.props.primaryId FROM FileMetadataDO fm " +
            "    WHERE fm.publicId = :fileId" +
            ")")
    int updateFileShareProps(
            @Param("fileId") String fileId,
            @Param("shareId") String shareId,
            @Param("shareProps") ShareProperties shareProps);

    @Query("UPDATE FilePropsDO f SET " +
            "f.shareBase = :shareBase, " +
            "f.shareId = :shareId, " +
            "f.shareProps = :shareProps " +
            "WHERE f.publicId = :id"
    )
    @Modifying
    int updateShareBaseById(Boolean shareBase, String shareId, ShareProperties shareProps, String id);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = null, " +
            "fp.subShare = null, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.primaryId IN (" +
            "    SELECT fm.props.primaryId FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId AND fm.path LIKE :pathPrefix ESCAPE '\\'" +
            ")")
    int unsetFolderShareProps(@Param("userId") String userId,
                              @Param("pathPrefix") String pathPrefix,
                              @Param("shareProps") ShareProperties shareProps);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = null, " +
            "fp.subShare = null, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.publicId = :fileId")
    int unsetFileShareProps(@Param("fileId") String fileId, @Param("shareProps") ShareProperties shareProps);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.publicId = :fileId")
    void updateSharePropsByFileId(@Param("fileId") String fileId, @Param("shareProps") ShareProperties shareProps);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareBase = null, " +
            "fp.subShare = true " +
            "WHERE fp.primaryId IN (" +
            "    SELECT fm.props.primaryId FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId AND fm.path LIKE :pathPrefix ESCAPE '\\'" +
            "    AND fp.shareBase = true" +
            ")")
    int setSubShareFormShareBase(@Param("userId") String userId,
                                 @Param("pathPrefix") String pathPrefix);

    @Query("SELECT p.shareProps FROM FilePropsDO p WHERE p.publicId = :id")
    ShareProperties findSharePropsById(String id);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.props = :props " +
            "WHERE fp.publicId = :fileId")
    void setPropsById(String fileId, OtherProperties props);

    Optional<FilePropsDO> findByPublicId(String publicId);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET fp.transcodeVideo = null WHERE fp.transcodeVideo = 0")
    void unsetTranscodeVideo();

    @Modifying
    @Query("UPDATE FilePropsDO fp SET fp.transcodeVideo = :status WHERE fp.publicId IN :fileIds")
    int updateTranscodeVideoByIdIn(List<String> fileIds, int status);

    long countByTranscodeVideo(Integer transcodeVideo);

    @Query("SELECT fp " +
            "FROM FilePropsDO fp " +
            "WHERE fp.publicId IN :fileId")
    VideoInfoDO findVideoInfoById(String id);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.props = :otherProperties " +
            "WHERE fp.primaryId IN (" +
            "    SELECT fm.props.primaryId FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId " +
            "    AND fm.path = :path " +
            "    AND fm.name = :name" +
            ")")
    void setOtherPropsByUserIdAndPathAndName(OtherProperties otherProperties, String userId, String path, String name);

    @Query(
            value = "SELECT p.public_id FROM file_props p " +
                    "JOIN files f ON p.id = f.id " +
                    "WHERE " +
                    "f.content_type LIKE 'video%' AND " +
                    // 条件组2: 原始视频不符合标准
                    "(" +
                    "   JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.height')) IS NULL OR " +
                    "   CAST(JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.height')) AS UNSIGNED) > :heightCond OR " +
                    "   CAST(JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.bitrateNum')) AS UNSIGNED) > :bitrateCond OR " +
                    "   CAST(JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.frameRate')) AS UNSIGNED) > :frameRateCond" +
                    ") AND " +
                    // 条件组3: 转码后视频不符合目标
                    "(" +
                    // 对于非相等比较，确保进行正确的类型转换
                    "   CAST(JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.toHeight')) AS UNSIGNED) != :targetHeight OR " +
                    "   CAST(JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.toBitrate')) AS UNSIGNED) != :targetBitrate OR " +
                    "   CAST(JSON_UNQUOTE(JSON_EXTRACT(p.props, '$.video.toFrameRate')) AS DECIMAL(10,2)) != :targetFrameRate" +
                    ")",
            nativeQuery = true
    )
    List<String> findFileIdsToTranscodeMySQL(
            @Param("heightCond") int heightCond,
            @Param("bitrateCond") int bitrateCond,
            @Param("frameRateCond") int frameRateCond,
            @Param("targetHeight") int targetHeight,
            @Param("targetBitrate") int targetBitrate,
            @Param("targetFrameRate") double targetFrameRate
    );

    @Query(
            value = "SELECT p.public_id FROM file_props p " +
                    "JOIN files f ON p.id = f.id " +
                    "WHERE " +
                    // PostgreSQL的LIKE连接符是 ||
                    "f.content_type LIKE 'video' || '%' AND " +
                    // 条件组2: 原始视频不符合标准
                    "(" +
                    "   (p.props -> 'video' ->> 'height') IS NULL OR " +
                    "   (p.props -> 'video' ->> 'height')::int > :heightCond OR " +
                    "   (p.props -> 'video' ->> 'bitrateNum')::int > :bitrateCond OR " +
                    "   (p.props -> 'video' ->> 'frameRate')::int > :frameRateCond" +
                    ") AND " +
                    // 条件组3: 转码后视频不符合目标
                    "(" +
                    "   (p.props -> 'video' ->> 'toHeight')::int != :targetHeight OR " +
                    "   (p.props -> 'video' ->> 'toBitrate')::int != :targetBitrate OR " +
                    "   (p.props -> 'video' ->> 'toFrameRate')::float != :targetFrameRate" +
                    ")",
            nativeQuery = true
    )
    List<String> findFileIdsToTranscodePostgreSQL(
            @Param("heightCond") int heightCond,
            @Param("bitrateCond") int bitrateCond,
            @Param("frameRateCond") int frameRateCond,
            @Param("targetHeight") int targetHeight,
            @Param("targetBitrate") int targetBitrate,
            @Param("targetFrameRate") double targetFrameRate
    );

    @Query(
            value = "SELECT p.public_id FROM file_props p " +
                    "JOIN files f ON p.id = f.id " +
                    "WHERE " +
                    "f.content_type LIKE 'video' || '%' AND " +
                    // 条件组2: 原始视频不符合标准
                    "(" +
                    // SQLite的json_extract直接返回基本类型，无需 unquote
                    "   json_extract(p.props, '$.video.height') IS NULL OR " +
                    "   CAST(json_extract(p.props, '$.video.height') AS INTEGER) > :heightCond OR " +
                    "   CAST(json_extract(p.props, '$.video.bitrateNum') AS INTEGER) > :bitrateCond OR " +
                    "   CAST(json_extract(p.props, '$.video.frameRate') AS INTEGER) > :frameRateCond" +
                    ") AND " +
                    // 条件组3: 转码后视频不符合目标
                    "(" +
                    "   CAST(json_extract(p.props, '$.video.toHeight') AS INTEGER) != :targetHeight OR " +
                    "   CAST(json_extract(p.props, '$.video.toBitrate') AS INTEGER) != :targetBitrate OR " +
                    "   CAST(json_extract(p.props, '$.video.toFrameRate') AS REAL) != :targetFrameRate" +
                    ")",
            nativeQuery = true
    )
    List<String> findFileIdsToTranscodeSQLite(
            @Param("heightCond") int heightCond,
            @Param("bitrateCond") int bitrateCond,
            @Param("frameRateCond") int frameRateCond,
            @Param("targetHeight") int targetHeight,
            @Param("targetBitrate") int targetBitrate,
            @Param("targetFrameRate") double targetFrameRate
    );

    @Modifying
    @Query("DELETE FROM FilePropsDO p WHERE p.publicId IN :publicIds")
    void deleteAllByPublicIdIn(Collection<String> publicIds);

    @Modifying
    @Query("DELETE FROM FilePropsDO p WHERE p.publicId = :publicId")
    void deleteByPublicId(String publicId);

}
