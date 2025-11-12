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
import java.nio.file.StandardCopyOption;

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

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
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

    /**
     * 检查分片是否完整
     */
    public boolean isChunksComplete(String noteId, int totalChunks) {
        Path chunkDir = filePersistenceService.buildFilePathForWrite(noteId, BURN_CHUNKS_DIR, String.valueOf(totalChunks)).toPath();

        if (!Files.exists(chunkDir)) {
            return false;
        }

        for (int i = 0; i < totalChunks; i++) {
            if (!Files.exists(chunkDir.resolve(String.valueOf(i)))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取已上传的分片数量
     */
    public int getUploadedChunksCount(String noteId) {
        File file = filePersistenceService.buildFilePathForRead(noteId, BURN_CHUNKS_DIR, "1");
        File chunkDir = file.getParentFile();

        if (!FileUtil.exist(chunkDir)) {
            return 0;
        }

        File[] files = chunkDir.listFiles();
        return files != null ? files.length : 0;
    }

}
