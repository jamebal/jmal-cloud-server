package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.dto.*;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FileMetadataRepository extends JpaRepository<FileMetadataDO, Long>, JpaSpecificationExecutor<FileMetadataDO> {

    @Query("SELECT f.publicId FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND p.shareBase = true " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    List<String> findIdSubShares(
            @Param("userId") String userId,
            @Param("pathPrefix") String pathPrefix
    );

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.publicId IN :ids")
    List<FileMetadataDO> findAllByIdIn(List<String> ids);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.publicId = :id")
    FileMetadataDO findOneById(String id);

    @Query("SELECT (count(f) > 0) FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND p.shareBase = true " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    boolean existsFolderSubShare(@Param("userId") String userId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.userId = :userId " +
            "AND f.publicId LIKE :idPrefix ESCAPE '\\'")
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
    Optional<String> findMountFilePath(String mountFileId, String userId);

    @Query("SELECT f.publicId FROM FileMetadataDO f WHERE f.publicId IN :publicIds")
    List<String> findByPublicIdIn(Collection<String> publicIds);

    void removeByMountFileIdIn(Collection<String> mountFileIds);

    @Query("SELECT f.publicId FROM FileMetadataDO f WHERE f.mountFileId IN :mountFileIds")
    List<String> findAllPublicIdsByMountFileId(List<String> mountFileIds);

    /**
     * 计算用户的文件总大小
     *
     * @param userId 用户ID
     * @return 总大小（字节）
     */
    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadataDO f WHERE f.userId = :userId AND f.isFolder = false")
    Long calculateTotalSizeByUserId(@Param("userId") String userId);

    @Query("SELECT (count(f) > 0) FROM FileMetadataDO f " +
            "WHERE f.name = :name AND" +
            "(:publicId IS NULL OR f.publicId <> :publicId)"
    )
    boolean existsByNameAndPublicIdNot(String name, String publicId);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.name, f.path, f.userId) FROM FileMetadataDO f WHERE f.publicId = :id")
    Optional<FileBaseDTO> findFileBaseDTO(String id);

    boolean existsByUserIdAndPathAndNameIn(String userId, String path, Collection<String> names);

    boolean existsByUserIdAndPathAndMd5(String userId, String path, String md5);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.publicId, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    Optional<FileBaseDTO> findFileBaseDTOByUserIdAndPathAndName(String userId, String path, String name);

    @Query("SELECT f.publicId " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
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
            "WHERE f.publicId = :id"
    )
    int updateModifyFile(
            String id,
            Long length,
            String md5,
            String suffix,
            String contentType,
            LocalDateTime updateDate
    );

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.publicId, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.publicId in :fileIds"
    )
    List<FileBaseDTO> findAllFileBaseDTOByIdIn(List<String> fileIds);

    @Query("SELECT f.publicId " +
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

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.publicId, f.name, f.path, f.userId, f.isFolder)  " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    List<FileBaseDTO> findFileBaseDTOAllByUserIdAndPathPrefix(String userId, String pathPrefix);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.ossFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.publicId = :id")
    Optional<FileBaseOssPathDTO> findFileBaseOssPathDTOById(String id);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.ossFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.publicId in :ids")
    List<FileBaseOssPathDTO> findFileBaseOssPathDTOByIdIn(List<String> ids);

    @Modifying
    @Query("DELETE " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    void removeByUserIdAndPathAndName(String userId, String path, String name);

    long countByDelTag(Integer delTag);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.publicId, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.delTag = :delTag " +
            "ORDER BY f.isFolder DESC"
    )
    List<FileBaseDTO> findFileBaseDTOByDelTagOfLimit(Integer delTag, Pageable pageable);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET " +
            "f.delTag = 0 " +
            "WHERE f.publicId = :fileId AND f.delTag = 1"
    )
    Integer unsetDelTag(String fileId);

    @Query("SELECT f.publicId FROM FileMetadataDO f WHERE f.userId IN :userIdList")
    List<String> findAllIdsByUserIdIn(List<String> userIdList);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.publicId, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f " +
            "WHERE f.publicId = :id"
    )
    Optional<FileBaseDTO> findFileBaseDTOById(String id);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.isFavorite = :isFavorite WHERE f.publicId IN :fileIds")
    void setIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.name = :name, f.suffix = :suffix WHERE f.publicId = :fileId")
    void setNameAndSuffixById(String fileId, String name, String suffix);

    @Query("SELECT f.publicId FROM FileMetadataDO f WHERE f.userId = :userId AND f.mountFileId IS NOT NULL")
    Page<String> findIdsByUserIdAndMountFileIdIsNotNull(String userId, Pageable pageable);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseMountDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.mountFileId) FROM FileMetadataDO f WHERE f.userId = :userId AND f.path = :path AND f.isFolder = true")
    List<FileBaseMountDTO> findAllByUserIdAndPathAndIsFolderIsTrue(String userId, String path);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.hasContent = true WHERE f.publicId = :fileId")
    void setContentById(String fileId);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.path = :path WHERE f.publicId = :fileId")
    void setPathById(String fileId, String path);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.name = :name WHERE f.mountFileId = :mountFileId")
    void setNameByMountFileId(String mountFileId, String name);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p WHERE f.userId = :userId AND f.path = :path AND f.name IN :names")
    List<FileMetadataDO> findAllByUserIdAndPathAndNameIn(String userId, String path, List<String> names);

    @Query("SELECT f.name FROM FileMetadataDO f WHERE f.publicId IN :ids")
    List<String> findFilenameListByIdIn(List<String> ids);


    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseAllDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.suffix, f.size, f.contentType) " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path")
    List<FileBaseAllDTO> findAllFileBaseAllDTOByUserIdAndPath(String userId, String path);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.updateDate = :time WHERE f.publicId = :id")
    void setUpdateDateById(String id, LocalDateTime time);

    boolean existsByPublicId(String publicId);

    void deleteByPublicId(String publicId);

    void deleteAllByPublicIdIn(Collection<String> publicIds);

    Optional<FileMetadataDO> findByPublicId(String publicId);

    @Query("SELECT f FROM FileMetadataDO f JOIN FETCH f.props p WHERE f.path = :path")
    List<FileMetadataDO> findAllByPath(String path);

    @Query("SELECT f " +
            "FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.path LIKE :pathPrefix ESCAPE '\\'")
    List<FileMetadataDO> findAllByPathPrefix(String pathPrefix);

    @Query("SELECT f " +
            "FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.mountFileId LIKE :pathPrefix ESCAPE '\\'")
    List<FileMetadataDO> findAllByMountFileIdPrefix(String pathPrefix);

    @Query("SELECT f " +
            "FROM FileMetadataDO f JOIN FETCH f.props p " +
            "WHERE f.publicId LIKE :idPrefix ESCAPE '\\'")
    List<FileMetadataDO> findAllByIdPrefix(String idPrefix);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseOperationPermissionDTO(" +
            "f.publicId, f.name, f.path, f.userId, f.isFolder, f.props.shareProps) " +
            "FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.publicId = :id")
    Optional<FileBaseOperationPermissionDTO> findFileBaseOperationPermissionDTOById(String id);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.publicId, f.name, f.path, f.userId, f.isFolder) " +
            "FROM FileMetadataDO f JOIN f.props p " +
            "WHERE p.transcodeVideo = :status"
    )
    List<FileBaseDTO> findFileBaseDTOByNotTranscodeVideo(int status);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.luceneIndex = :luceneIndex WHERE f.publicId IN :fileIdList")
    void updateLuceneIndexStatusByIdIn(List<String> fileIdList, int luceneIndex);

    long countByLuceneIndex(int luceneIndex);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseLuceneDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.isFavorite, p.remark, p.tags, a.tagIds, f.etag, f.size, f.uploadDate) " +
            "FROM FileMetadataDO f LEFT JOIN ArticleDO a ON a.fileMetadata = f JOIN f.props p " +
            "WHERE f.luceneIndex = :status")
    List<FileBaseLuceneDTO> findFileBaseLuceneDTOByLuceneIndex(int status, Pageable pageable);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseLuceneDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.isFavorite, p.remark, p.tags, a.tagIds, f.etag, f.size, f.uploadDate) " +
            "FROM FileMetadataDO f LEFT JOIN ArticleDO a ON a.fileMetadata = f JOIN f.props p " +
            "WHERE f.publicId IN :fileIdList")
    List<FileBaseLuceneDTO> findFileBaseLuceneDTOByIdIn(List<String> fileIdList);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.delTag = null WHERE f.publicId IN :fileIdList AND f.delTag = 1")
    void unsetDelTagByIdIn(List<String> fileIdList);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.delTag = null WHERE f.delTag = 1")
    void unsetDelTag();

    // @Modifying
    // @Query("UPDATE FileMetadataDO f SET f.delTag = 1 " +
    //         "WHERE " +
    //         "f.mountFileId IS NULL AND " +
    //         "(:userId IS NULL OR f.userId = :userId) AND " +
    //         "(:pathPrefix IS NULL OR f.path LIKE :pathPrefix ESCAPE '\\') AND " +
    //         "f.mountFileId IS NULL AND " +
    //         "NOT EXISTS (" +
    //         "   SELECT 1 FROM ArticleDO a " +
    //         "   WHERE a.fileMetadata = f AND " +
    //         "   a.alonePage IS NULL AND " +
    //         "   a.release IS NULL" +
    //         ")")
    // void markAsDeletedWithArticleConditions(
    //         @Param("userId") String userId,
    //         @Param("pathPrefix") String pathPrefix);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.delTag = 1 " +
            "WHERE " +
            "f.mountFileId IS NULL AND " +
            "(:userId IS NULL OR f.userId = :userId) AND " +
            "(:pathPrefix IS NULL OR f.path LIKE :pathPrefix ESCAPE '\\')"
    )
    void setDelTagByUserIdAndPathPrefix(String userId, String pathPrefix);

    boolean existsByLuceneIndexIsLessThanEqual(Integer luceneIndexIsLessThan);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.luceneIndex = null WHERE f.luceneIndex <= 1")
    void resetIndexStatus();

    int countByOssFolderIsNotNull();

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(f.name, f.path, f.userId) " +
            "FROM FileMetadataDO f " +
            "WHERE f.publicId IN (" +
            "   SELECT fm.mountFileId FROM FileMetadataDO fm " +
            "   WHERE fm.userId = :userId AND fm.mountFileId IS NOT NULL" +
            ")"
    )
    List<FileBaseDTO> findMountFileBaseDTOByUserId(String userId);

    @EntityGraph(attributePaths = "props")
    @Query("SELECT f FROM FileMetadataDO f " +
            "WHERE f.userId = :userId AND f.contentType LIKE :contentTypePrefix ESCAPE '\\'")
    Page<FileMetadataDO> findAllByUserIdAndContentTypeStartingWith(String userId, String contentTypePrefix, Pageable pageable);

    @EntityGraph(attributePaths = "props")
    Page<FileMetadataDO> findAllByUserIdAndSuffixIn(String userId, List<String> suffixList, Pageable pageable);

    @EntityGraph(attributePaths = "props")
    Page<FileMetadataDO> findAllByUserIdAndPath(String userId, String path, Pageable pageable);

    @EntityGraph(attributePaths = "props")
    Page<FileMetadataDO> findAllByUserIdAndIsFavoriteIsTrue(String userId, Pageable pageable);

    @EntityGraph(attributePaths = "props")
    Page<FileMetadataDO> findAllByUserIdAndIsFolder(String userId, Boolean isFolder, Pageable pageable);
}
