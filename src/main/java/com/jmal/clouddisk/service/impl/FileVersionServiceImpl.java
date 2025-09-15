package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
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
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.util.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.io.IOUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.context.ApplicationEventPublisher;
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

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description 文件版本管理
 * @date 2023/5/9 17:53
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FileVersionServiceImpl implements IFileVersionService {

    private final CommonUserFileService commonUserFileService;

    private final FileService fileService;

    private final IFileHistoryDAO fileHistoryDAO;

    private final IFileDAO fileDAO;

    private final FileProperties fileProperties;

    private final UserLoginHolder userLoginHolder;

    private final CommonUserService userService;

    private final ApplicationEventPublisher eventPublisher;

    private final Cache<String, Object> fileLocks = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Override
    public void saveFileVersion(String fileUsername, String relativePath, String userId) {
        saveFileVersion(fileUsername, relativePath, userId, userLoginHolder.getUsername());
    }

    private void saveFileVersion(String fileUsername, String relativePath, String userId, String operator) {
        File file = new File(Paths.get(fileProperties.getRootDir(), fileUsername, relativePath).toString());
        String filepath = Paths.get(fileUsername, relativePath).toString();
        Object lock = fileLocks.get(filepath, _ -> new Object());
        if (lock != null) {
            synchronized (lock) {
                if (CaffeineUtil.hasFileHistoryCache(filepath)) {
                    return;
                }
                FileDocument fileDocument = commonUserFileService.getFileDocumentByPath(filepath, userId);
                long size = file.length();
                String updateDate = fileDocument.getUpdateDate().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN));
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

    @Override
    public void asyncSaveFileVersion(String fileUsername, File file, String operator) {
        Path physicalPath = file.toPath();
        Path userRootPath = Paths.get(fileProperties.getRootDir(), fileUsername);
        Path relativeNioPath = userRootPath.relativize(physicalPath);
        Completable.fromAction(() -> saveFileVersion(fileUsername, relativeNioPath.toString(), userService.getUserIdByUserName(fileUsername), operator)).subscribeOn(Schedulers.io()).subscribe();
    }


    @Override
    public void saveFileVersion(AbstractOssObject abstractOssObject, String fileId) {
        Object lock = fileLocks.get(fileId, _ -> new Object());
        if (lock != null) {
            synchronized (lock) {
                if (CaffeineUtil.hasFileHistoryCache(fileId)) {
                    return;
                }
                long size = abstractOssObject.getContentLength();
                String filename = Paths.get(abstractOssObject.getKey()).getFileName().toString();
                String updateDate = DateUtil.format(abstractOssObject.getFileInfo().getLastModified(), DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN));
                Metadata metadata = setMetadata(size, fileId, filename, updateDate, userLoginHolder.getUsername(), null);
                if (metadata == null) return;
                try (InputStream inputStream = abstractOssObject.getInputStream();
                     InputStream gzipInputStream = MyFileUtils.gzipCompress(inputStream, metadata.getCompression())) {
                    fileHistoryDAO.store(gzipInputStream, fileId, metadata);
                    CaffeineUtil.setFileHistoryCache(fileId);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
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
        Sort sort = Sort.by(Sort.Direction.DESC, Constants.UPLOAD_DATE);
        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize, sort);
        Page<GridFSBO> page = fileHistoryDAO.findPageByFileId(fileId, pageable);
        return ResultUtil.success(page.getContent()).setCount(page.getTotalElements());
    }

    @Override
    public ResponseResult<List<OfficeHistory>> officeListFileVersion(String path, Integer pageSize, Integer pageIndex) {
        ResponseResult<List<GridFSBO>> result = listFileVersion(path, pageSize, pageIndex);
        List<OfficeHistory> officeHistoryList = result.getData().stream().map(gridFSBO -> {
            OfficeHistory officeHistory = new OfficeHistory();
            officeHistory.setCreated(gridFSBO.getMetadata().getTime());
            officeHistory.setKey(gridFSBO.getId());
            // 将时间设置为版本号, 格式转换为MM-dd
            officeHistory.setVersion(gridFSBO.getUploadDate().format(DateTimeFormatter.ofPattern("MM-dd")));
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
        return ResultUtil.success(officeHistoryList).setCount(result.getCount());
    }

    @Override
    public ResponseResult<List<GridFSBO>> listFileVersionByPath(String path, Integer pageSize, Integer pageIndex) {
        String fileId = null;
        String username = userLoginHolder.getUsername();
        Path prePth = Paths.get(username, path);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            fileId = WebOssService.getObjectName(prePth, ossPath, false);
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
    public FileDocument getFileById(String gridFSId) {
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
    public void deleteOne(String id) {
        fileHistoryDAO.deleteByIdIn(List.of(id));
    }

    @Override
    public void rename(String sourceFileId, String destinationFileId) {
        fileHistoryDAO.updateFileId(sourceFileId, destinationFileId);
    }

    @Override
    public Long recovery(String gridFSId) {
        FileHistoryDTO fileHistoryDTO = fileHistoryDAO.getFileHistoryDTO(gridFSId);
        if (fileHistoryDTO == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        String fileId = fileHistoryDTO.getFileId();
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
    public ResponseEntity<InputStreamResource> readHistoryFile(String gridFSId) {
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

}
