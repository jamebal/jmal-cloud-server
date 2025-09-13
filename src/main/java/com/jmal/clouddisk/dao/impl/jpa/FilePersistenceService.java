package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.Trash;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilePersistenceService {

    private final FileProperties fileProperties;

    /**
     * 从 FileDocument 中持久化内容到文件系统
     *
     * @param fileDocument 源数据对象
     */
    public void persistContents(FileDocument fileDocument) {
        if (fileDocument == null || fileDocument.getId() == null) {
            return;
        }

        writeContentToFile(fileDocument.getId(), Constants.CONTENT, fileDocument.getContent());
        writeContentToFile(fileDocument.getId(), Constants.CONTENT_TEXT, fileDocument.getContentText());
        writeContentToFile(fileDocument.getId(), Constants.CONTENT_HTML, fileDocument.getHtml());
        writeContentToFile(fileDocument.getId(), Constants.CONTENT_DRAFT, fileDocument.getDraft());
    }

    /**
     * 从 Trash 中持久化内容到文件系统
     *
     * @param trash 源数据对象
     */
    public void persistContents(Trash trash) {
        if (trash == null || trash.getId() == null) {
            return;
        }
        writeContentToFile(trash.getId(), Constants.CONTENT, trash.getContent());
    }

    public void readContents(FileMetadataDO fileMetadataDO, FileDocument fileDocument) {
        if (fileMetadataDO == null || fileMetadataDO.getId() == null || fileDocument == null) {
            return;
        }

        if (BooleanUtil.isTrue(fileMetadataDO.getHasContent())) {
            readContent(fileDocument.getId(), Constants.CONTENT).ifPresent(inputStream -> {
                try {
                    fileDocument.setContent(inputStream.readAllBytes());
                } catch (IOException e) {
                    throw new FilePersistenceException("Failed to read content for fileId: " + fileDocument.getId(), e);
                }
            });
        }

        if (BooleanUtil.isTrue(fileMetadataDO.getHasContentText())) {
            readContent(fileDocument.getId(), Constants.CONTENT_TEXT).ifPresent(inputStream -> {
                try (inputStream) {
                    fileDocument.setContentText(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new FilePersistenceException("Failed to read contentText for fileId: " + fileDocument.getId(), e);
                }
            });
        }

        if (BooleanUtil.isTrue(fileMetadataDO.getHasHtml())) {
            readContent(fileDocument.getId(), Constants.CONTENT_HTML).ifPresent(inputStream -> {
                try (inputStream) {
                    fileDocument.setHtml(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new FilePersistenceException("Failed to read html for fileId: " + fileDocument.getId(), e);
                }
            });
        }
    }

    public void persistDraft(String fileId, String content) {
        writeContentToFile(fileId, Constants.CONTENT_DRAFT, content);
    }

    public void delDraft(String fileId) {
        deleteContents(fileId, Constants.CONTENT_DRAFT);
    }

    /**
     * 根据文件ID和内容类型读取文件内容
     *
     * @param fileId 文件ID
     * @param subDir 内容类型 (content, contentText, html)
     * @return 文件的输入流 (InputStream)。如果文件不存在，返回 Optional.empty()
     */
    public Optional<InputStream> readContent(String fileId, String subDir) {
        try {
            // 1. 复用完全相同的路径构建逻辑
            File fileToRead = buildFilePathForRead(fileId, subDir);

            // 2. 检查文件是否存在且可读
            if (fileToRead.exists() && fileToRead.isFile() && fileToRead.canRead()) {
                // 3. 返回文件输入流
                return Optional.of(new FileInputStream(fileToRead));
            } else {
                // 文件不存在或不可读，返回空
                return Optional.empty();
            }
        } catch (FileNotFoundException e) {
            // FileInputStream 构造函数可能抛出此异常，尽管我们已经检查了 exists()
            // 但为了代码健壮性，还是处理一下。
            log.warn("File not found although it existed a moment ago. Race condition? FileId: {}", fileId);
            return Optional.empty();
        }
    }

    public void deleteContents(List<String> fileIds) {
        fileIds.forEach(this::deleteContents);
    }

    /**
     * 删除与一个文件ID相关的所有内容和目录
     *
     * @param fileId 要删除的文件ID
     */
    public void deleteContents(String fileId) {
        if (CharSequenceUtil.isBlank(fileId) || fileId.length() < 4) {
            throw new CommonException("Attempted to delete contents with an invalid fileId: " + fileId);
        }
        // 构建最内层的、包含所有内容的父目录
        Path fileContainerPath = buildFilePathForDelete(fileId);
        FileUtil.del(fileContainerPath);
    }

    /**
     * 删除与一个文件ID相关的所有内容和目录
     *
     * @param fileId 要删除的文件ID
     */
    public void deleteContents(String fileId, String subDir) {
        if (CharSequenceUtil.isBlank(fileId) || fileId.length() < 4) {
            throw new CommonException("Attempted to delete contents with an invalid fileId: " + fileId);
        }
        // 构建最内层的、包含所有内容的父目录
        File file = buildFilePathForRead(fileId, subDir);
        FileUtil.del(file);
    }

    private void writeContentToFile(String fileId, String subDir, byte[] content) {
        if (content == null) {
            return;
        }
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            File targetFile = buildFilePathForWrite(fileId, subDir);
            FileUtil.writeFromStream(stream, targetFile);
        } catch (IOException e) {
            throw new FilePersistenceException("Failed to write content for fileId: " + fileId, e);
        }
    }

    private void writeContentToFile(String fileId, String subDir, String content) {
        if (CharSequenceUtil.isBlank(content)) {
            return;
        }
        writeContentToFile(fileId, subDir, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为读取操作创建一个独立的路径构建方法
     */
    private File buildFilePathForRead(String fileId, String subDir) {
        if (CharSequenceUtil.isBlank(fileId) || fileId.length() < 4) {
            throw new IllegalArgumentException("File ID is invalid for sharding: " + fileId);
        }

        return Paths.get(
                fileProperties.getRootDir(),
                fileProperties.getJmalcloudDBDir(),
                "data",
                getLevel1(fileId),     // 第一级分片目录
                getLevel2(fileId),     // 第二级分片目录
                fileId,     // 文件ID作为最内层目录
                subDir,     // 功能子目录
                fileId      // 最终的文件名
        ).toFile();
    }

    private Path buildFilePathForDelete(String fileId) {
        return Paths.get(
                fileProperties.getRootDir(),
                fileProperties.getJmalcloudDBDir(),
                "data",
                getLevel1(fileId),
                getLevel2(fileId),
                fileId
        );
    }

    private static String getLevel2(String fileId) {
        return fileId.substring(2, 4);
    }

    private static String getLevel1(String fileId) {
        return fileId.substring(0, 2);
    }

    private File buildFilePathForWrite(String fileId, String subDir) {
        File file = buildFilePathForRead(fileId, subDir); // 复用路径构建逻辑
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            FileUtil.mkdir(parentDir);
        }
        return file;
    }

    public static class FilePersistenceException extends RuntimeException {
        public FilePersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
