package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.dto.FileBaseAllDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseMountDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FileMetadataRepository extends JpaRepository<FileMetadataDO, String> , JpaSpecificationExecutor<FileMetadataDO> {

    @Query("SELECT f.id FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND p.shareBase = true " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
        // 使用 LIKE 进行前缀匹配
    List<String> findIdSubShares(
            @Param("userId") String userId,
            @Param("pathPrefix") String pathPrefix
    );

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.id IN :ids")
    List<FileMetadataDO> findAllByIdIn(List<String> ids);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.id = :id")
    FileMetadataDO findOneById(String id);

    @Query("SELECT (count(f) > 0) FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND p.shareBase = true " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    boolean existsFolderSubShare(@Param("userId") String userId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.userId = :userId " +
            "AND f.id LIKE :idPrefix ESCAPE '\\'")
    List<FileMetadataDO> findAllByUserIdAndIdPrefix(String userId, String idPrefix);

    @Query("SELECT f " +
            "FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    Optional<FileMetadataDO> findByNameAndUserIdAndPath(String name, String userId, String path);

    boolean existsByUserIdAndMountFileId(String userId, String mountFileId);

    @Query("SELECT f.path FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.mountFileId = :mountFileId ")
    Optional<String> findMountFilePath(String userId, String mountFileId);

    @Query("SELECT f.id FROM FileMetadataDO f " +
            "WHERE f.id in :ids ")
    List<String> findByIdIn(Collection<String> ids);

    void removeByMountFileId(String mountFileId);

    /**
     * 计算用户的文件总大小
     *
     * @param userId 用户ID
     * @return 总大小（字节）
     */
    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadataDO f WHERE f.userId = :userId AND f.isFolder = false")
    Long calculateTotalSizeByUserId(@Param("userId") String userId);


    @Modifying
    @Query("DELETE FROM FileMetadataDO f WHERE f.userId IN :userIdList")
    void deleteAllByUserIdInBatch(List<String> userIdList);

    boolean existsByNameAndIdNotIn(String name, Collection<String> ids);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.name, f.path, f.userId) FROM FileMetadataDO f WHERE f.id = :id")
    Optional<FileBaseDTO> findFileBaseDTO(String id);

    boolean existsByUserIdAndPathAndNameIn(String userId, String path, Collection<String> names);

    boolean existsByUserIdAndPathAndMd5(String userId, String path, String md5);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.id, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :user " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    Optional<FileBaseDTO> findFileBaseDTOByUserIdAndPathAndName(String userId, String path, String name);

    @Query("SELECT f.id " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :user " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    Optional<String> findIdByUserIdAndPathAndName(String userId, String path, String name);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET " +
            "f.size = :length, " +
            "f.md5 = :md5, " +
            "f.suffix = :suffix, " +
            "f.contentType = :contentType, " +
            "f.updateDate = :updateDate " +
            "WHERE f.id = :id"
    )
    int updateModifyFile(
            String id,
            Long length,
            String md5,
            String suffix,
            String contentType,
            LocalDateTime updateDate
    );

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.id, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.id in :fileIds"
    )
    List<FileBaseDTO> findAllFileBaseDTOByIdIn(List<String> fileIds);

    @Query("SELECT f.id " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    List<String> findAllIdsByUserIdAndPathPrefix(String userId, String pathPrefix);

    @Modifying
    @Query("DELETE FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    Integer removeAllByUserIdAndPathPrefix(String userId, String pathPrefix);

    @Query("SELECT f " +
            "FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.userId = :userId " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    List<FileMetadataDO> findAllByUserIdAndPathPrefix(String userId, String pathPrefix);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.id, f.name, f.path, f.userId, f.isFolder)  " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    List<FileBaseDTO> findFileBaseDTOAllByUserIdAndPathPrefix(String userId, String pathPrefix);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO(f.id, f.name, f.path, f.userId, f.isFolder, f.ossFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.id = :id")
    Optional<FileBaseOssPathDTO> findFileBaseOssPathDTOById(String id);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO(f.id, f.name, f.path, f.userId, f.isFolder, f.ossFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.id in :ids")
    List<FileBaseOssPathDTO> findFileBaseOssPathDTOByIdIn(List<String> ids);

    @Modifying
    @Query("DELETE " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :user " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    void removeByUserIdAndPathAndName(String userId, String path, String name);

    long countByDelTag(Integer delTag);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.id, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.delTag = :delTag " +
            "ORDER BY f.isFolder DESC"
    )
    List<FileBaseDTO> findFileBaseDTOByDelTagOfLimit(Integer delTag, Pageable pageable);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET " +
            "f.delTag = 0 " +
            "WHERE f.id = :fileId AND f.delTag = 1"
    )
    Integer unsetDelTag(String fileId);

    @Query("SELECT f.id FROM FileMetadataDO f WHERE f.userId IN :userIdList")
    List<String> findAllIdsByUserIdIn(List<String> userIdList);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.id, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.id = :id"
    )
    Optional<FileBaseDTO> findFileBaseDTOById(String id);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.isFavorite = :isFavorite WHERE f.id IN :fileIds")
    void setIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.name = :name, f.suffix = :suffix WHERE f.id = :fileId")
    void setNameAndSuffixById(String fileId, String name, String suffix);

    @Query("SELECT f.id FROM FileMetadataDO f WHERE f.userId = :userId AND f.mountFileId IS NOT NULL")
    Page<String> findIdsByUserIdAndMountFileIdIsNotNull(String userId, Pageable pageable);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseMountDTO(f.id, f.name, f.path, f.userId, f.isFolder, f.mountFileId) FROM FileMetadataDO f WHERE f.path = :path AND f.isFolder = true")
    List<FileBaseMountDTO> findAllByPathAndIsFolderIsTrue(String path);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.hasContent = true WHERE f.id = :fileId")
    void setContentById(String fileId);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.path = :path WHERE f.id = :fileId")
    void setPathById(String fileId, String path);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.name = :name WHERE f.mountFileId = :mountFileId")
    void setNameByMountFileId(String mountFileId, String name);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p WHERE f.userId = :userId AND f.path = :path AND f.name IN :names")
    List<FileMetadataDO> findAllByUserIdAndPathAndNameIn(String userId, String path, List<String> names);

    @Query("SELECT f.name FROM FileMetadataDO f WHERE f.id IN :ids")
    List<String> findFilenameListByIdIn(List<String> ids);


    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseAllDTO(f.id, f.name, f.path, f.userId, f.isFolder, f.suffix, f.size, f.contentType) " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path")
    List<FileBaseAllDTO> findAllFileBaseAllDTOByUserIdAndPath(String userId, String path);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.updateDate = :time WHERE f.id = :id")
    void setUpdateDateById(String id, LocalDateTime time);
}
