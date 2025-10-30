package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import org.springframework.data.domain.Page;

public interface ILogDAO {

    void save(LogOperation logOperation);

    Page<LogOperation> findAllByQuery(LogOperationDTO logOperationDTO, String currentUsername, String currentUserId, boolean isAdministrators);

    long countByUrl(String url);

    Page<LogOperation> findFileOperationHistoryByFileId(LogOperationDTO logOperationDTO, String fileId, String currentUserId, String currentUsername);
}
