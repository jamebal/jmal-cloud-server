package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FilePropsDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FilePropsRepository extends JpaRepository<FilePropsDO, String> {

    @Query("UPDATE FilePropsDO p SET p.shareBase = true WHERE p.id = :fileId")
    @Modifying
    void setSubShareByFileId(String fileId);

    @Query("UPDATE FilePropsDO p SET p.shareBase = null WHERE p.id = :fileId")
    @Modifying
    void unsetSubShareByFileId(String fileId);

}
