package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.IFolderSizeDAO;
import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FolderSizeJpaRepository;
import com.jmal.clouddisk.model.file.FileDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FolderSizeJpaImpl implements IFolderSizeDAO {

    private final FolderSizeJpaRepository fileDocumentRepository;

    @Override
    public List<FileDocument> findFoldersNeedUpdateSize(int batchSize) {
        log.debug("查询需要更新大小的文件夹，批次大小: {}", batchSize);

        try {
            Pageable pageable = PageRequest.of(0, batchSize);
            List<FileDocument> folders = fileDocumentRepository.findFoldersWithoutSize(pageable);

            log.debug("找到 {} 个需要更新大小的文件夹", folders.size());
            return folders;
        } catch (Exception e) {
            log.error("查询需要更新大小的文件夹失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询文件夹失败", e);
        }
    }

    @Override
    @Transactional
    public void updateFileSize(String fileId, long size) {
        log.debug("更新文件大小: fileId={}, size={}", fileId, size);

        try {
            int updatedCount = fileDocumentRepository.updateFileSize(fileId, size);

            if (updatedCount > 0) {
                log.debug("文件大小更新成功: fileId={}, size={}", fileId, size);
            } else {
                log.warn("文件大小更新失败，未找到文件: fileId={}", fileId);
            }
        } catch (Exception e) {
            log.error("更新文件大小失败: fileId={}, size={}, error={}", fileId, size, e.getMessage(), e);
            throw new RuntimeException("更新文件大小失败", e);
        }
    }

    @Override
    public boolean hasNeedUpdateSizeInDb() {
        log.debug("检查是否还有需要更新大小的文件夹");

        try {
            boolean exists = fileDocumentRepository.existsFoldersWithoutSize();
            log.debug("是否存在需要更新大小的文件夹: {}", exists);
            return exists;
        } catch (Exception e) {
            log.error("检查需要更新大小的文件夹失败: {}", e.getMessage(), e);
            throw new RuntimeException("检查文件夹失败", e);
        }
    }

    @Override
    public long totalSizeNeedUpdateSizeInDb() {
        log.debug("统计需要更新大小的文件夹数量");

        try {
            long count = fileDocumentRepository.countFoldersWithoutSize();
            log.debug("需要更新大小的文件夹数量: {}", count);
            return count;
        } catch (Exception e) {
            log.error("统计需要更新大小的文件夹数量失败: {}", e.getMessage(), e);
            throw new RuntimeException("统计文件夹数量失败", e);
        }
    }

    @Override
    @Transactional
    public void clearFolderSizInDb() {
        log.info("清空数据库中所有文件夹的大小信息");

        try {
            int updatedCount = fileDocumentRepository.clearAllFolderSizes();
            log.info("已清空 {} 个文件夹的大小信息", updatedCount);
        } catch (Exception e) {
            log.error("清空文件夹大小信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("清空文件夹大小信息失败", e);
        }
    }

}
