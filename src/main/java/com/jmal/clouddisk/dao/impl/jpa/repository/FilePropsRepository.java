package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
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

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FilePropsRepository extends JpaRepository<FilePropsDO, String> , JpaSpecificationExecutor<FilePropsDO> {

    @Query("UPDATE FilePropsDO p SET p.shareBase = true WHERE p.id = :fileId")
    @Modifying
    void setSubShareByFileId(String fileId);

    @Query("UPDATE FilePropsDO p SET p.shareBase = null WHERE p.id = :fileId")
    @Modifying
    void unsetSubShareByFileId(String fileId);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseTagsDTO(p.id, p.tags) FROM FilePropsDO p WHERE p.id IN :fileIds")
    List<FileBaseTagsDTO> findTagsByIdIn(@Param("fileIds") Collection<String> fileIds);

    /**
     * 只更新tags字段
     */
    @Modifying
    @Query("UPDATE FilePropsDO p SET p.tags = :tags WHERE p.id = :id")
    void updateTagsForFile(@Param("id") String fileId, @Param("tags") List<Tag> tags);

    @Modifying
    @Query("UPDATE FilePropsDO p SET p.tags = :tags WHERE p.id IN :fileIds")
    void updateTagsForFiles(@Param("fileIds") List<String> fileIds, @Param("tags") List<Tag> tags);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = :shareId, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.id IN (" +
            "    SELECT fm.id FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId AND fm.path LIKE :pathPrefix%" +
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
            "WHERE fp.id IN (" +
            "    SELECT fm.id FROM FileMetadataDO fm " +
            "    WHERE fm.id = :fileId" +
            ")")
    int updateFileShareProps(
            @Param("fileId") String fileId,
            @Param("shareId") String shareId,
            @Param("shareProps") ShareProperties shareProps);

    @Query("update FilePropsDO f set f.shareBase = :shareBase where f.id = :id")
    @Modifying
    int updateShareBaseById(Boolean shareBase, String id);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = null, " +
            "fp.subShare = null, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.id IN (" +
            "    SELECT fm.id FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId AND fm.path LIKE :pathPrefix%" +
            ")")
    int unsetFolderShareProps(@Param("userId") String userId,
                              @Param("pathPrefix") String pathPrefix,
                              @Param("shareProps") ShareProperties shareProps);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareId = null, " +
            "fp.subShare = null, " +
            "fp.shareProps = :shareProps " +
            "WHERE fp.id = :fileId")
    int unsetFileShareProps(@Param("fileId") String fileId, @Param("shareProps") ShareProperties shareProps);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.shareBase = null, " +
            "fp.subShare = true " +
            "WHERE fp.id IN (" +
            "    SELECT fm.id FROM FileMetadataDO fm " +
            "    WHERE fm.userId = :userId AND fm.path LIKE :pathPrefix% " +
            "    AND fp.shareBase = true" +
            ")")
    int setSubShareFormShareBase(@Param("userId") String userId,
                                 @Param("pathPrefix") String pathPrefix);

    @Query("SELECT p.shareProps FROM FilePropsDO p WHERE p.id = :id")
    ShareProperties findSharePropsById(String id);

    @Modifying
    @Query("UPDATE FilePropsDO fp SET " +
            "fp.props = :props " +
            "WHERE fp.id = :fileId")
    void setPropsById(String fileId, OtherProperties props);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseTagsDTO(fp.id, fp.tags) " +
            "FROM FilePropsDO fp " +
            "WHERE fp.id IN :tagIds")
    List<FileBaseTagsDTO> findAllFileBaseTagsDTOByTagIdIn(List<String> attr0);
}
