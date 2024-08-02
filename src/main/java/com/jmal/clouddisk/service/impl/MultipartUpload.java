package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.UploadResponse;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.util.CaffeineUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author jmal
 * @Description 分片上传
 * @date 2023/4/7 17:20
 */
@Service
@Slf4j
public class MultipartUpload {

    @Autowired
    private FileProperties fileProperties;

    @Autowired
    private CommonFileService commonFileService;

    @Autowired
    private WebOssService webOssService;

    /***
     * 断点恢复上传缓存(已上传的缓存)
     */
    private static final Cache<String, CopyOnWriteArrayList<Integer>> resumeCache = CaffeineUtil.getResumeCache();
    /***
     * 上传大文件是需要分片上传，再合并
     * 已写入(合并)的分片缓存
     */
    private static final Cache<String, CopyOnWriteArrayList<Integer>> writtenCache = CaffeineUtil.getWrittenCache();
    /***
     * 未写入(合并)的分片缓存
     */
    private static final Cache<String, CopyOnWriteArrayList<Integer>> unWrittenCache = CaffeineUtil.getUnWrittenCacheCache();
    /***
     * 合并文件的写入锁缓存
     */
    private static final Cache<String, Lock> chunkWriteLockCache = CaffeineUtil.getChunkWriteLockCache();

    /**
     * 上传分片文件
     *
     * @param upload         UploadApiParamDTO
     * @param uploadResponse UploadResponse
     * @param md5            md5
     * @param file           MultipartFile
     */
    public void uploadChunkFile(UploadApiParamDTO upload, UploadResponse uploadResponse, String md5, MultipartFile file) throws IOException {
        // 多个分片
        // 落地保存文件
        // 这时保存的每个块, 块先存好, 后续会调合并接口, 将所有块合成一个大文件
        // 保存在用户的tmp目录下
        File chunkFile = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), md5, Convert.toStr(upload.getChunkNumber())).toFile();
        FileUtil.writeFromStream(file.getInputStream(), chunkFile);
        setResumeCache(upload);
        uploadResponse.setUpload(true);
        // 追加分片
        appendChunkFile(upload);
        // 检测是否已经上传完了所有分片,上传完了则需要合并
        if (checkIsNeedMerge(upload)) {
            uploadResponse.setMerge(true);
        }
    }

    /**
     * 合并文件
     *
     * @param upload UploadApiParamDTO
     */
    public UploadResponse mergeFile(UploadApiParamDTO upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFilename());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return webOssService.mergeFile(ossPath, prePth, upload);
        }

        String md5 = upload.getIdentifier();
        Path file = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), upload.getFilename());
        Path outputFile = Paths.get(fileProperties.getRootDir(), upload.getUsername(), commonFileService.getUserDirectoryFilePath(upload));
        // 清除缓存
        resumeCache.invalidate(md5);
        writtenCache.invalidate(md5);
        unWrittenCache.invalidate(md5);
        chunkWriteLockCache.invalidate(md5);
        Path chunkDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), md5);
        PathUtil.del(chunkDir);
        if (!Files.exists(outputFile)) {
            Files.createFile(outputFile);
        }
        PathUtil.move(file, outputFile, true);
        uploadResponse.setUpload(true);
        CaffeineUtil.setUploadFileCache(outputFile.toFile().getAbsolutePath());
        commonFileService.createFile(upload.getUsername(), outputFile.toFile(), null, null);
        return uploadResponse;
    }

    /***
     * 合并文件追加分片
     * @param upload UploadApiParamDTO
     */
    private void appendChunkFile(UploadApiParamDTO upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        // 未写入的分片
        CopyOnWriteArrayList<Integer> unWrittenChunks = unWrittenCache.get(md5, key -> new CopyOnWriteArrayList<>());
        if (unWrittenChunks != null && !unWrittenChunks.contains(chunkNumber)) {
            unWrittenChunks.add(chunkNumber);
            unWrittenCache.put(md5, unWrittenChunks);
        }
        // 已写入的分片
        CopyOnWriteArrayList<Integer> writtenChunks = writtenCache.get(md5, key -> new CopyOnWriteArrayList<>());
        Path filePath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), upload.getFilename());
        if (unWrittenChunks == null || writtenChunks == null) {
            return;
        }
        Lock lock = chunkWriteLockCache.get(md5, key -> new ReentrantLock());
        if (lock != null) {
            lock.lock();
        }
        try {
            if (Files.exists(filePath) && !writtenChunks.isEmpty()) {
                // 继续追加
                unWrittenChunks.forEach(unWrittenChunk -> appendFile(upload, unWrittenChunks, writtenChunks));
            } else {
                // 首次写入
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                appendFile(upload, unWrittenChunks, writtenChunks);
            }
        } catch (Exception e) {
            throw new CommonException(ExceptionType.FAIL_MERGE_FILE);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    public UploadResponse checkChunk(UploadApiParamDTO upload) throws IOException {

        boolean checkExist = upload.getFilenames() != null && !upload.getFilenames().isEmpty();
        if (checkExist) {
            for (String filename : upload.getFilenames()) {
                Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), filename);
                String ossPath = CaffeineUtil.getOssPath(prePth);
                if (ossPath != null) {
                    return webOssService.checkExist(ossPath, prePth);
                }
            }
        } else {
            Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFilename());
            String ossPath = CaffeineUtil.getOssPath(prePth);
            if (ossPath != null) {
                return webOssService.checkChunk(ossPath, prePth, upload);
            }
        }
        UploadResponse uploadResponse = new UploadResponse();

        String path = commonFileService.getUserDirectory(upload.getCurrentDirectory());
        if (checkExist) {
            // 将upload.getFiles()中的filename提取出来
            FileDocument fileDocument = commonFileService.exist(path, upload.getUserId(), upload.getFilenames());
            if (fileDocument != null) {
                // 文件已存在
                uploadResponse.setPass(true);
                uploadResponse.setExist(true);
                return uploadResponse;
            }
        } else {
            String md5 = upload.getIdentifier();
            String relativePath = upload.getRelativePath();
            path += relativePath.substring(0, relativePath.length() - upload.getFilename().length());

            FileDocument fileDocument = commonFileService.getByMd5(path, upload.getUserId(), md5);
            if (fileDocument != null) {
                // 文件已存在
                uploadResponse.setPass(true);
            } else {
                int totalChunks = upload.getTotalChunks();
                List<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
                // 返回已存在的分片
                uploadResponse.setResume(chunks);
                assert chunks != null;
                if (totalChunks == chunks.size()) {
                    // 文件不存在,并且已经上传了所有的分片,则合并保存文件
                    mergeFile(upload);
                }
            }
        }
        uploadResponse.setUpload(true);
        return uploadResponse;
    }

    /***
     * 追加分片操作
     * @param upload UploadApiParamDTO
     * @param unWrittenChunks  未写入的分片集合
     * @param writtenChunks    已写入的分片集合
     */
    private void appendFile(UploadApiParamDTO upload, CopyOnWriteArrayList<Integer> unWrittenChunks, CopyOnWriteArrayList<Integer> writtenChunks) {
        // 需要继续追加分片索引
        int chunk = 1;
        if (!writtenChunks.isEmpty()) {
            chunk = writtenChunks.get(writtenChunks.size() - 1) + 1;
        }
        if (!unWrittenChunks.contains(chunk)) {
            return;
        }
        String md5 = upload.getIdentifier();
        // 分片文件
        File chunkFile = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), md5, String.valueOf(chunk)).toFile();
        // 目标文件
        File outputFile = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), upload.getFilename()).toFile();
        long position = outputFile.length();
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile, true);
             FileChannel outChannel = fileOutputStream.getChannel()) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(FileUtil.readBytes(chunkFile));
            int writeLength = outChannel.write(byteBuffer, position);
            if (writeLength != chunkFile.length()) {
                log.error("writeLength: {}, chunkFileLength: {}", writeLength, chunkFile.length());
            }
            writtenChunks.add(chunk);
            writtenCache.put(md5, writtenChunks);
            unWrittenChunks.remove((Integer) chunk);
            unWrittenCache.put(md5, unWrittenChunks);
        } catch (IOException e) {
            throw new CommonException(ExceptionType.FAIL_MERGE_FILE);
        }
    }

    /***
     * 缓存已上传的分片
     * @param upload UploadApiParamDTO
     */
    private void setResumeCache(UploadApiParamDTO upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        CopyOnWriteArrayList<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
        assert chunks != null;
        if (!chunks.contains(chunkNumber)) {
            chunks.add(chunkNumber);
            resumeCache.put(md5, chunks);
        }
    }

    /***
     * 获取已经保存的分片索引
     */
    private CopyOnWriteArrayList<Integer> getSavedChunk(UploadApiParamDTO upload) {
        String md5 = upload.getIdentifier();
        return resumeCache.get(md5, key -> createResumeCache(upload));
    }

    /***
     * 检测是否需要合并
     */
    private boolean checkIsNeedMerge(UploadApiParamDTO upload) {
        int totalChunks = upload.getTotalChunks();
        CopyOnWriteArrayList<Integer> chunkList = getSavedChunk(upload);
        return totalChunks == chunkList.size();
    }

    /***
     * 读取分片文件是否存在
     * @return 已经保存的分片索引列表
     */
    private CopyOnWriteArrayList<Integer> createResumeCache(UploadApiParamDTO upload) {
        CopyOnWriteArrayList<Integer> resumeList = new CopyOnWriteArrayList<>();
        String md5 = upload.getIdentifier();
        // 读取tmp分片目录所有文件
        File f = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), upload.getUsername(), md5).toFile();
        if (f.exists()) {
            // 排除目录，只要文件
            File[] fileArray = f.listFiles(pathName -> !pathName.isDirectory());
            if (fileArray != null) {
                for (File file : fileArray) {
                    // 分片文件
                    int resume = Integer.parseInt(file.getName());
                    resumeList.add(resume);
                }
            }
        }
        return resumeList;
    }


}
