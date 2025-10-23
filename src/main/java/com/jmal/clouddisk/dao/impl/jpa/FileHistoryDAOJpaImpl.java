package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFileHistoryDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.filehistory.FileHistoryOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import com.jmal.clouddisk.model.file.FileHistoryDO;
import com.jmal.clouddisk.model.file.FileHistoryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileHistoryDAOJpaImpl implements IFileHistoryDAO {

    private final FileHistoryRepository fileHistoryRepository;

    private final IWriteService writeService;

    private final FilePersistenceService persistenceService;

    @Override
    public void store(InputStream inputStream, String fileId, Metadata metadata) {
        // 保存到数据库
        FileHistoryDO fileHistoryDO = new FileHistoryDO(fileId, metadata);
        try {
            writeService.submit(new FileHistoryOperation.Create(fileHistoryDO ,inputStream)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public FileHistoryDTO getFileHistoryDTO(String fileHistoryId) {
        return fileHistoryRepository.findFileHistoryDTOById(fileHistoryId);
    }

    @Override
    public InputStream getInputStream(String fileId, String fileHistoryId) throws IOException {
        return persistenceService.getFileHistoryInputStream(fileId, fileHistoryId);
    }

    @Override
    public Page<GridFSBO> findPageByFileId(String fileId, Pageable pageable) {
        return fileHistoryRepository.findGridFSBOByFileId(fileId, pageable);
    }

    @Override
    public void deleteAllByFileIdIn(List<String> fileIds) {
        try {
            writeService.submit(new FileHistoryOperation.DeleteByFileIds(fileIds)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("删除文件历史失败", e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void deleteByIdIn(List<String> fileHistoryIds) {
        try {
            writeService.submit(new FileHistoryOperation.DeleteByIds(fileHistoryIds)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("删除文件历史失败", e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void updateFileId(String sourceFileId, String destinationFileId) {
        try {
            writeService.submit(new FileHistoryOperation.UpdateFileId(sourceFileId, destinationFileId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("更新文件历史的fileId失败", e);
            throw new CommonException(e.getMessage());
        }
    }
}
