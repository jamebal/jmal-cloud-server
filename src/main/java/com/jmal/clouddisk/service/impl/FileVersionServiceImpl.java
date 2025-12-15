package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.CharsetUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IFileHistoryDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.LuceneIndexQueueEvent;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileHistoryDTO;
import com.jmal.clouddisk.office.OfficeHistory;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileNameUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.Pair;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.io.IOUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author jmal
 * @Description 文件版本管理
 * @date 2023/5/9 17:53
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FileVersionServiceImpl implements IFileVersionService, ApplicationListener<FileVersionEvent> {

    private final CommonUserFileService commonUserFileService;

    private final FileService fileService;

    private final IFileHistoryDAO fileHistoryDAO;

    private final IFileDAO fileDAO;

    private final FileProperties fileProperties;

    private final UserLoginHolder userLoginHolder;

    private final ApplicationEventPublisher eventPublisher;

    private final Cache<String, Object> fileLocks = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10_0000)
            .build();

    @Override
    public void onApplicationEvent(FileVersionEvent event) {
        saveFileVersion(event.getFileUsername(), event.getPath(), event.getUserId(), event.getOperator());
    }

    private void saveFileVersion(String fileUsername, String relativePath, String userId, String operator) {
        File file = new File(Paths.get(fileProperties.getRootDir(), fileUsername, relativePath).toString());
        if (!file.exists() || !file.isFile()) {
            return;
        }
        String filepath = Paths.get(fileUsername, relativePath).toString();
        Object lock = fileLocks.get(filepath, _ -> new Object());
        if (lock != null) {
            synchronized (lock) {
                if (CaffeineUtil.hasFileHistoryCache(filepath)) {
                    return;
                }
                FileDocument fileDocument = commonUserFileService.getFileDocumentByPath(filepath, userId);
                long size = file.length();
                String updateDate = CommonUserFileService.getFileLastModifiedTime(file).format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN));
                Metadata metadata = setMetadata(size, filepath, file.getName(), updateDate, operator, getCharset(file));
                if (metadata == null) return;
                try (InputStream inputStream = new FileInputStream(file);
                     InputStream gzipInputStream = MyFileUtils.gzipCompress(inputStream, metadata.getCompression())) {
                    fileHistoryDAO.store(gzipInputStream, fileDocument.getId(), metadata);
                    CaffeineUtil.setFileHistoryCache(filepath);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private static String getCharset(File file) {
        String charset;
        try {
            charset = UniversalDetector.detectCharset(file);
        } catch (IOException e) {
            charset = CharsetUtil.UTF_8;
        }
        return charset;
    }

    private static String getCharset(InputStream inputStream) {
        String charset;
        try (inputStream) {
            charset = UniversalDetector.detectCharset(inputStream);
        } catch (Exception e) {
            charset = CharsetUtil.UTF_8;
        }
        return charset;
    }

    /**
     * 设置历史文件自定义元数据
     *
     * @param size       文件大小
     * @param filepath   filepath(以username开头)
     * @param filename   filename
     * @param updateDate 文件最后修改时间
     */
    private static Metadata setMetadata(long size, String filepath, String filename, String updateDate, String username, String charset) {
        Metadata metadata = new Metadata();
        metadata.setFilepath(filepath);
        metadata.setFilename(filename);
        metadata.setTime(updateDate);
        metadata.setSize(size);
        metadata.setOperator(username);
        metadata.setCharset(charset);
        if (size == 0) {
            // 无内容，不用存历史版本
            return null;
        }
        if (size >= 1024) {
            metadata.setCompression("gzip");
        }
        return metadata;
    }

    private Pair<InputStream, String> getInputStream(FileHistoryDTO fileHistoryDTO) {
        try {
            InputStream inputStream = fileHistoryDAO.getInputStream(fileHistoryDTO.getFileId(), fileHistoryDTO.getId());
            if (inputStream == null) {
                return Pair.of(new ByteArrayInputStream(new byte[0]), CharsetUtil.UTF_8);
            }
            if (CharSequenceUtil.isBlank(fileHistoryDTO.getCharset())) {
                // 没有保存字符集，自动检测
                String charset = getCharset(fileHistoryDAO.getInputStream(fileHistoryDTO.getFileId(), fileHistoryDTO.getId()));
                fileHistoryDTO.setCharset(charset);
            }
            return MyFileUtils.gzipDecompress(inputStream, "gzip".equals(fileHistoryDTO.getCompression()), fileHistoryDTO.getCharset());
        } catch (IOException e) {
            return Pair.of(new ByteArrayInputStream(new byte[0]), CharsetUtil.UTF_8);
        }
    }

    @Override
    public ResponseResult<List<GridFSBO>> listFileVersion(String fileId, Integer pageSize, Integer pageIndex) {
        Path prePath = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            String objectName = WebOssService.getObjectName(prePath, ossPath, false);
            return getS3VersionListResult(pageSize, pageIndex, ossPath, objectName);
        }
        Sort sort = Sort.by(Sort.Direction.DESC, Constants.UPLOAD_DATE);
        Page<GridFSBO> page = listFileVersionBySort(fileId, pageSize, pageIndex, sort);
        return ResultUtil.success(page.getContent()).setCount(page.getTotalElements());
    }


    private static ResponseResult<List<GridFSBO>> getS3VersionListResult(Integer pageSize, Integer pageIndex, String ossPath, String objectName) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        Page<GridFSBO> page = ossService.listObjectVersions(objectName, pageSize, pageIndex);
        return ResultUtil.success(page.getContent()).setCount(page.getTotalElements());
    }

    private Page<GridFSBO> listFileVersionBySort(String fileId, Integer pageSize, Integer pageIndex, Sort sort) {
        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize, sort);
        return fileHistoryDAO.findPageByFileId(fileId, pageable);
    }

    @Override
    public ResponseResult<List<OfficeHistory>> officeListFileVersion(String path, Integer pageSize, Integer pageIndex) {
        Sort sort = Sort.by(Sort.Direction.ASC, Constants.UPLOAD_DATE);
        Page<GridFSBO> page = listFileVersionBySort(path, pageSize, pageIndex, sort);
        List<GridFSBO> gridFSBOList = page.getContent();
        final long versionOffset = (long)(pageIndex - 1) * pageSize;
        List<OfficeHistory> officeHistoryList = IntStream.range(0, gridFSBOList.size())
                .mapToObj(i -> {
                    GridFSBO gridFSBO = gridFSBOList.get(i);
            OfficeHistory officeHistory = new OfficeHistory();
            officeHistory.setCreated(gridFSBO.getMetadata().getTime());
            officeHistory.setKey(gridFSBO.getId());
            // 用顺序设置版本号
            officeHistory.setVersion(Convert.toStr(versionOffset + i + 1));
            OfficeHistory.User user = new OfficeHistory.User();
            String username = gridFSBO.getMetadata().getOperator();
            if (CharSequenceUtil.isBlank(username)) {
                username = "Unknown";
            }
            user.setId(username);
            user.setName(username);
            officeHistory.setUser(user);
            return officeHistory;
        }).toList();
        return ResultUtil.success(officeHistoryList).setCount(page.getTotalElements());
    }

    @Override
    public ResponseResult<List<GridFSBO>> listFileVersionByPath(String path, Integer pageSize, Integer pageIndex) {
        String fileId = null;
        String username = userLoginHolder.getUsername();
        Path prePth = Paths.get(username, path);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            String objectName = WebOssService.getObjectName(prePth, ossPath, false);
            return getS3VersionListResult(pageSize, pageIndex, ossPath, objectName);
        } else {
            Path filePath = Paths.get(FileNameUtils.safeDecode(path));
            String relativePath = File.separator;
            if (filePath.getNameCount() > 1) {
                relativePath += filePath.subpath(0, filePath.getNameCount() - 1) + File.separator;
            }
            String userId = userLoginHolder.getUserId();
            FileDocument fileDocument = commonUserFileService.getFileDocumentByPath(relativePath, filePath.getFileName().toString(), userId);
            if (fileDocument == null) {
                return ResultUtil.success(new ArrayList<>());
            }
            if (fileDocument.getId() != null) {
                fileId = fileDocument.getId();
            }
        }
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.success(new ArrayList<>());
        }
        return listFileVersion(fileId, pageSize, pageIndex);
    }

    @Override
    public FileDocument getFileById(String gridFSId, String fileId) {
        Path prePath = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            String objectName = WebOssService.getObjectName(prePath, ossPath, false);
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName, gridFSId);
            if (abstractOssObject == null) {
                return null;
            }
            FileDocument fileDocument = new FileDocument();
            fileDocument.setName(abstractOssObject.getFileInfo().getName());
            fileDocument.setSize(abstractOssObject.getFileInfo().getSize());
            try (InputStream inputStream = abstractOssObject.getInputStream()) {
                String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                fileDocument.setContentText(content);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            return fileDocument;
        }
        FileHistoryDTO fileHistoryDTO = fileHistoryDAO.getFileHistoryDTO(gridFSId);
        if (fileHistoryDTO == null) {
            return null;
        }
        FileDocument fileDocument = fileService.getById(fileHistoryDTO.getFileId());
        if (fileDocument == null) {
            return null;
        }
        fileDocument.setSize(fileHistoryDTO.getSize());
        fileDocument.setName(fileHistoryDTO.getFilename());
        Pair<InputStream, String> pair = getInputStream(fileHistoryDTO);
        try (InputStream inputStream = pair.getLeft()) {
            String content = IOUtils.toString(inputStream, pair.getRight());
            fileDocument.setContentText(content);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return fileDocument;
    }

    @Override
    public StreamingResponseBody getStreamFileById(String gridFSId) {
        return outputStream -> {
            FileHistoryDTO fileHistoryDTO = fileHistoryDAO.getFileHistoryDTO(gridFSId);
            if (fileHistoryDTO == null) {
                return;
            }
            Pair<InputStream, String> pair = getInputStream(fileHistoryDTO);
            try (InputStream inputStream = pair.getLeft()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
                outputStream.flush();
            } catch (ClientAbortException ignored) {
                // ignored
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        };
    }

    @Override
    public void deleteAll(List<String> fileIds) {
        fileHistoryDAO.deleteAllByFileIdIn(fileIds);
    }

    @Override
    public void deleteAll(String fileId) {
        fileHistoryDAO.deleteAllByFileIdIn(List.of(fileId));
    }

    @Override
    public void deleteOne(String id, String fileId) {
        Path prePath = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            String objectName = WebOssService.getObjectName(prePath, ossPath, false);
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            boolean deleted = ossService.deleteObject(objectName, id);
            if (!deleted) {
                throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "删除版本失败");
            }
            return;
        }
        fileHistoryDAO.deleteByIdIn(List.of(id));
    }

    @Override
    public void rename(String sourceFileId, String destinationFileId) {
        fileHistoryDAO.updateFileId(sourceFileId, destinationFileId);
    }

    @Override
    public Long recovery(String gridFSId, String fileId) {
        Path prePath = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            String objectName = WebOssService.getObjectName(prePath, ossPath, false);
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            ossService.restoreVersion(objectName, gridFSId);
            return Instant.now().toEpochMilli();
        }
        FileHistoryDTO fileHistoryDTO = fileHistoryDAO.getFileHistoryDTO(gridFSId);
        if (fileHistoryDTO == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        fileId = fileHistoryDTO.getFileId();
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        String username = userLoginHolder.getUsername();
        if (CharSequenceUtil.isBlank(username)) {
            throw new CommonException(ExceptionType.LOGIN_EXCEPTION);
        }
        File file = Paths.get(fileProperties.getRootDir(), username, fileDocument.getPath(), fileHistoryDTO.getFilename()).toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        if (CommonFileService.isLock(file, fileProperties.getRootDir(), username)) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }
        LocalDateTime time = LocalDateTimeUtil.now();
        Pair<InputStream, String> pair = getInputStream(fileHistoryDTO);
        try (InputStream inputStream = pair.getLeft()) {
            FileUtil.writeFromStream(inputStream, file);
            fileDAO.setUpdateDateById(fileId, time);
            eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileId));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public ResponseEntity<InputStreamResource> readHistoryFile(String gridFSId, String fileId) {
        Path prePath = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            return getInputStreamResourceResponseEntity(gridFSId, prePath, ossPath);
        }
        FileHistoryDTO fileHistoryDTO = fileHistoryDAO.getFileHistoryDTO(gridFSId);
        if (fileHistoryDTO == null) {
            return ResponseEntity.notFound().build();
        }
        Pair<InputStream, String> pair = getInputStream(fileHistoryDTO);
        InputStream inputStream = pair.getLeft();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(fileHistoryDTO.getFilename(), StandardCharsets.UTF_8) + "\"");
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/octet-stream"))
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            try {
                inputStream.close();
            } catch (Exception ignore) {
            }
        }
        return ResponseEntity.notFound().build();
    }

    private static ResponseEntity<InputStreamResource> getInputStreamResourceResponseEntity(String gridFSId, Path prePath, String ossPath) {
        String objectName = WebOssService.getObjectName(prePath, ossPath, false);
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName, gridFSId);
        if (abstractOssObject == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            InputStream inputStream = abstractOssObject.getInputStream();
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(abstractOssObject.getFileInfo().getName(), StandardCharsets.UTF_8) + "\"");
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/octet-stream"))
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

}
