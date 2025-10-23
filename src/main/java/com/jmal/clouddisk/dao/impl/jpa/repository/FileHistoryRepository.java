package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.file.FileHistoryDO;
import com.jmal.clouddisk.model.file.FileHistoryDTO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FileHistoryRepository extends JpaRepository<FileHistoryDO, String>, JpaSpecificationExecutor<FileHistoryDO> {

    @Query("SELECT new com.jmal.clouddisk.model.file.FileHistoryDTO(f.id, f.fileId, f.filename, f.compression, f.charset, f.size) FROM FileHistoryDO f WHERE f.id = :id")
    FileHistoryDTO findFileHistoryDTOById(String id);

    @Query("SELECT new com.jmal.clouddisk.model.GridFSBO(f.id, f.fileId, f.size, f.uploadDate, f.filepath, f.filename, f.time, f.operator, f.size) FROM FileHistoryDO f WHERE f.fileId = :fileId")
    Page<GridFSBO> findGridFSBOByFileId(String fileId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM FileHistoryDO f WHERE f.fileId IN :fileIds")
    void deleteAllByFileIdIn(Collection<String> fileIds);

    @Query("SELECT f.fileId FROM FileHistoryDO f WHERE f.id = :id")
    String findFileIdById(String id);

    @Modifying
    @Query("UPDATE FileHistoryDO f SET f.fileId = :destinationFileId WHERE f.fileId = :sourceFileId")
    void updateFileId(String sourceFileId, String destinationFileId);

}
