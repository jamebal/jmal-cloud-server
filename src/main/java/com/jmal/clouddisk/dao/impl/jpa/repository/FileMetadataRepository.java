package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FileBaseDTO;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FileMetadataRepository extends JpaRepository<FileMetadataDO, String> {

    @Query("SELECT f.id FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND p.shareBase = true " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
        // 使用 LIKE 进行前缀匹配
    List<String> findIdSubShares(
            @Param("userId") String userId,
            @Param("pathPrefix") String pathPrefix
    );

    @Query("SELECT (count(f) > 0) FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND p.shareBase = true " +
            "AND f.path LIKE :pathPrefix ESCAPE '\\'")
    boolean existsFolderSubShare(@Param("userId") String userId, @Param("pathPrefix") String pathPrefix);

    @Query("SELECT f FROM FileMetadataDO f JOIN f.props p " +
            "WHERE f.userId = :userId " +
            "AND f.id LIKE :idPrefix ESCAPE '\\'")
    List<FileMetadataDO> findAllByUserIdAndIdPrefix(String userId, String idPrefix);

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

    @Query("SELECT new com.jmal.clouddisk.model.file.FileBaseDTO(f.name, f.path, f.userId) FROM FileMetadataDO f WHERE f.id = :id")
    Optional<FileBaseDTO> findFileBaseDTO(String id);

}
