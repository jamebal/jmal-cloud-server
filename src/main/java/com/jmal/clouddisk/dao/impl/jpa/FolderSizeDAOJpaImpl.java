package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IFolderSizeDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FolderSizeRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FolderSizeDAOJpaImpl implements IFolderSizeDAO {

    private final FolderSizeRepository folderSizeRepository;

    private final IWriteService writeService;

    @Override
    public List<FileDocument> findFoldersNeedUpdateSize(int batchSize) {
        log.debug("查询需要更新大小的文件夹，批次大小: {}", batchSize);

        try {
            Pageable pageable = PageRequest.of(0, batchSize);
            List<FileMetadataDO> folders = folderSizeRepository.findFoldersWithoutSize(pageable);

            List<FileDocument> fileDocuments = folders.stream().map(fileMetadataDO -> {
                FileDocument fileDocument = new FileDocument();
                fileDocument.setId(fileMetadataDO.getId());
                fileDocument.setPath(fileMetadataDO.getPath());
                fileDocument.setName(fileMetadataDO.getName());
                fileDocument.setUserId(fileMetadataDO.getUserId());
                return fileDocument;
            }).toList();
            log.debug("找到 {} 个需要更新大小的文件夹", folders.size());
            return fileDocuments;
        } catch (Exception e) {
            log.error("查询需要更新大小的文件夹失败: {}", e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void updateFileSize(String fileId, long size) {
        log.debug("更新文件大小: fileId={}, size={}", fileId, size);

        try {
            CompletableFuture<Integer> future = writeService.submit(new FileOperation.UpdateFileSize(fileId, size));
            int updatedCount = future.get(10, TimeUnit.SECONDS);
            if (updatedCount > 0) {
                log.debug("文件大小更新成功: fileId={}, size={}", fileId, size);
            } else {
                log.warn("文件大小更新失败，未找到文件: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.error("更新文件大小失败: fileId={}, size={}, error={}", fileId, size, e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean hasNeedUpdateSizeInDb() {
        log.debug("检查是否还有需要更新大小的文件夹");

        try {
            boolean exists = folderSizeRepository.existsFoldersWithoutSize();
            log.debug("是否存在需要更新大小的文件夹: {}", exists);
            return exists;
        } catch (Exception e) {
            log.error("检查需要更新大小的文件夹失败: {}", e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public long totalSizeNeedUpdateSizeInDb() {
        log.debug("统计需要更新大小的文件夹数量");

        try {
            long count = folderSizeRepository.countFoldersWithoutSize();
            log.debug("需要更新大小的文件夹数量: {}", count);
            return count;
        } catch (Exception e) {
            log.error("统计需要更新大小的文件夹数量失败: {}", e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void clearFolderSizInDb() {
        log.debug("清空数据库中所有文件夹的大小信息");

        try {
            writeService.submit(new FileOperation.ClearAllFolderSizes()).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("清空文件夹大小信息失败: {}", e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

}
