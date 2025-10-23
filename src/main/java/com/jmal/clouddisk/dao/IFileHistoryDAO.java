package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import com.jmal.clouddisk.model.file.FileHistoryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface IFileHistoryDAO {

    void store(InputStream inputStream, String fileId, Metadata metadata);

    FileHistoryDTO getFileHistoryDTO(String fileHistoryId);

    InputStream getInputStream(String fileId, String fileHistoryId) throws IOException;

    Page<GridFSBO> findPageByFileId(String fileId, Pageable pageable);

    void deleteAllByFileIdIn(List<String> fileIds);

    void deleteByIdIn(List<String> fileHistoryIds);

    void updateFileId(String sourceFileId, String destinationFileId);
}
