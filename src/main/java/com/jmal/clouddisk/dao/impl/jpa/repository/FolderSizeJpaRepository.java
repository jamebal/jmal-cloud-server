package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileEntityDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FolderSizeJpaRepository extends JpaRepository<FileEntityDO, String> {

    /**
     * 查询需要更新大小的文件夹（size字段为null）
     */
    @Query("SELECT f FROM FileEntityDO f WHERE f.isFolder = true AND f.size IS NULL")
    List<FileDocument> findFoldersWithoutSize(Pageable pageable);

    /**
     * 检查是否存在需要更新大小的文件夹
     */
    @Query("SELECT COUNT(f) > 0 FROM FileEntityDO f WHERE f.isFolder = true AND f.size IS NULL")
    boolean existsFoldersWithoutSize();

    /**
     * 统计需要更新大小的文件夹数量
     */
    @Query("SELECT COUNT(f) FROM FileEntityDO f WHERE f.isFolder = true AND f.size IS NULL")
    long countFoldersWithoutSize();

    /**
     * 更新指定文件的大小
     */
    @Modifying
    @Query("UPDATE FileEntityDO f SET f.size = :size, f.updateDate = CURRENT_TIMESTAMP WHERE f.id = :fileId")
    int updateFileSize(@Param("fileId") String fileId, @Param("size") Long size);

    /**
     * 清空所有文件夹的大小信息
     */
    @Modifying
    @Query("UPDATE FileEntityDO f SET f.size = NULL WHERE f.isFolder = true")
    int clearAllFolderSizes();

}
