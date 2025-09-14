package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.file.dto.FileBaseMountDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IFileQueryDAO {

    Page<FileIntroVO> getFileIntroVO(UploadApiParamDTO upload);

    List<FileBaseMountDTO> getDirDocuments(UploadApiParamDTO upload);

    FileDocument findBaseFileDocumentById(String id, boolean excludeContent);
}
