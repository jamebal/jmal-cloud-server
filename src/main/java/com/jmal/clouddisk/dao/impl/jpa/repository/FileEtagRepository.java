package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FileEtagRepository extends JpaRepository<FileMetadataDO, Long> {

    long countByEtagIsNullAndIsFolderIsTrue();

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.needsEtagUpdate = true, f.lastEtagUpdateRequestAt = CURRENT_TIMESTAMP " +
            "WHERE f.etag IS NULL " +
            "AND f.isFolder = true"
    )
    int setFoldersWithoutEtag();

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.isFolder = false " +
            "AND f.path LIKE :pathPrefixForLike ESCAPE '\\'"
    )
    long sumSizeByUserIdAndPathPrefix(String userId, String pathPrefixForLike);

    @Query("SELECT COUNT(f) FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path LIKE :pathPrefixForLike ESCAPE '\\'"
    )
    int countByUserIdAndPathPrefix(String userId, String pathPrefixForLike);

    boolean existsByNeedsEtagUpdateIsTrueAndIsFolderIsTrue();

    @Query("SELECT f.etag FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    Optional<String> findEtagByUserIdAndPathAndName(String userId, String path, String name);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.etag = :etag, f.needsEtagUpdate = false " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path " +
            "AND f.name = :name"
    )
    void setEtagByUserIdAndPathAndName(String userId, String path, String name, String etag);

    boolean existsByUserIdAndPath(String userId, String path);

    long countByEtagIsNullAndIsFolderIsFalseAndPath(String path);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO(f.publicId, f.name, f.path, f.userId) " +
            "FROM FileMetadataDO f " +
            "WHERE f.etag IS NULL " +
            "AND f.isFolder = false " +
            "AND f.path = :path"
    )
    List<FileBaseEtagDTO> findFileBaseEtagDTOByEtagIsNullAndIsFolderIsFalseAndPath(String path, Pageable pageable);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO(f.publicId, f.name, f.path, f.userId, f.etag) " +
            "FROM FileMetadataDO f " +
            "WHERE f.needsEtagUpdate = true " +
            "AND (f.retryAt IS NULL OR f.retryAt <= :now) " +
            "AND f.isFolder = true " +
            "ORDER BY LENGTH(f.path) DESC, f.lastEtagUpdateRequestAt ASC " +
            "LIMIT 16"
    )
    List<FileBaseEtagDTO> findFileBaseEtagDTOByNeedUpdateFolder(Instant now);

    @Modifying
    @Query("UPDATE FileMetadataDO f " +
            "SET f.needsEtagUpdate = false,f.retryAt = null,f.etagUpdateFailedAttempts = null,f.lastEtagUpdateError = null " +
            "WHERE f.publicId = :fileId")
    void clearMarkUpdateById(String fileId);

    @Modifying
    @Query("UPDATE FileMetadataDO f " +
            "SET f.needsEtagUpdate = true,f.lastEtagUpdateRequestAt = CURRENT_TIMESTAMP " +
            "WHERE f.userId = :userId " +
            "AND f.path = :path " +
            "AND f.name = :name")
    int setMarkUpdateByUserIdAndPathAndName(String userId, String path, String name);

    boolean existsByUserIdAndPathAndName(String userId, String path, String name);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO(f.publicId, f.name, f.path, f.userId, f.isFolder, f.etag) " +
            "FROM FileMetadataDO f " +
            "WHERE f.userId = :userId " +
            "AND f.path = :userId1"
    )
    List<FileBaseEtagDTO> findFileBaseEtagDTOByUserIdAndPath(String userId, String userId1);

    @Modifying
    @Query("UPDATE FileMetadataDO f SET f.etag = :etag, f.size = :size, f.childrenCount = :childrenCount, f.updateDate = :now WHERE f.publicId = :fileId")
    Integer updateEtagAndSizeById(String fileId, String etag, long size, int childrenCount, LocalDateTime now);

    @Query("SELECT f.etagUpdateFailedAttempts FROM FileMetadataDO f WHERE f.publicId = :id")
    Optional<Integer> findEtagUpdateFailedAttemptsById(String id);

    @Modifying
    @Query("UPDATE FileMetadataDO f " +
            "SET f.etagUpdateFailedAttempts = :attempts, " +
            "f.lastEtagUpdateError = :errorMsg " +
            "WHERE f.publicId = :fileId")
    void setFailedEtagById(String fileId, int attempts, String errorMsg);

    @Modifying
    @Query("UPDATE FileMetadataDO f " +
            "SET f.etagUpdateFailedAttempts = :attempts, " +
            "f.lastEtagUpdateError = :errorMsg, " +
            "f.needsEtagUpdate = :needsEtagUpdate " +
            "WHERE f.publicId = :fileId")
    void setFailedEtagById(String fileId, int attempts, String errorMsg, Boolean needsEtagUpdate);

    @Modifying
    @Query("UPDATE FileMetadataDO f " +
            "SET f.retryAt = :nextRetryTime, " +
            "f.etagUpdateFailedAttempts = :attempts " +
            "WHERE f.publicId = :fileId")
    void setRetryAtById(String fileId, Instant nextRetryTime, int attempts);
}
