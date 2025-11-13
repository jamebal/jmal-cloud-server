package com.jmal.clouddisk.dao;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class BurnNoteFileService {

    private final FilePersistenceService filePersistenceService;
    private static final String BURN_CHUNKS_DIR = "burn_chunks";

    /**
     * 保存分片（从 MultipartFile）
     */
    public void saveChunk(String noteId, int chunkIndex, MultipartFile file) throws IOException {

        Path targetFile = filePersistenceService.buildFilePathForWrite(noteId, BURN_CHUNKS_DIR, String.valueOf(chunkIndex)).toPath();

        // 判断分片是否已存在，避免重复保存
        if (Files.exists(targetFile)) {
            log.debug("分片已存在，跳过保存: noteId={}, chunk={}", noteId, chunkIndex);
            return;
        }

        try (InputStream inputStream = file.getInputStream()) {
            FileUtil.writeFromStream(inputStream, targetFile.toFile());
        }

        log.debug("保存分片: noteId={}, chunk={}, size={}KB",
                noteId, chunkIndex, file.getSize() / 1024);
    }

    /**
     * 获取分片文件
     */
    public File getChunkFile(String noteId, int chunkIndex) throws IOException {
        Path chunkFile = filePersistenceService.buildFilePathForWrite(noteId, BURN_CHUNKS_DIR, String.valueOf(chunkIndex)).toPath();

        if (!Files.exists(chunkFile)) {
            throw new IOException("分片不存在: " + chunkIndex);
        }

        return chunkFile.toFile();
    }

    /**
     * 删除所有分片
     */
    public void deleteAllChunks(String noteId) {
        filePersistenceService.deleteContents(noteId);
    }

}
