package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.dto.FileTagsDTO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FilePropsDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FilePropsRepository extends JpaRepository<FilePropsDO, String> {

    @Query("UPDATE FilePropsDO p SET p.shareBase = true WHERE p.id = :fileId")
    @Modifying
    void setSubShareByFileId(String fileId);

    @Query("UPDATE FilePropsDO p SET p.shareBase = null WHERE p.id = :fileId")
    @Modifying
    void unsetSubShareByFileId(String fileId);

    @Query("SELECT new com.jmal.clouddisk.dao.impl.jpa.dto.FileTagsDTO(p.id, p.tags) FROM FilePropsDO p WHERE p.id IN :fileIds")
    List<FileTagsDTO> findTagsByIdIn(@Param("fileIds") Collection<String> fileIds);

    /**
     * 只更新tags字段
     */
    @Modifying
    @Query("UPDATE FilePropsDO p SET p.tags = :tags WHERE p.id = :id")
    void updateTagsForFile(@Param("id") String fileId, @Param("tags") Set<Tag> tags);

}
