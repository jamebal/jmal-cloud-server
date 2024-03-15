package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.io.IOUtils;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author jmal
 * @Description 文件版本管理
 * @date 2023/5/9 17:53
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class FileVersionServiceImpl implements IFileVersionService {

    private static final String COLLECTION_NAME = "fs.files";

    private final CommonFileService commonFileService;

    private final GridFsTemplate gridFsTemplate;

    private final MongoTemplate mongoTemplate;

    private final FileProperties fileProperties;

    private final IFileService fileService;

    private final UserLoginHolder userLoginHolder;

    @Override
    public void saveFileVersion(String username, String relativePath, String userId) {
        File file = new File(Paths.get(fileProperties.getRootDir(), username, relativePath).toString());
        String filepath = Paths.get(username, relativePath).toString();
        FileDocument fileDocument = commonFileService.getFileDocumentByPath(filepath, userId);
        long size = file.length();
        String updateDate = fileDocument.getUpdateDate().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN));
        Metadata metadata = setMetadata(size, filepath, file.getName(), updateDate, userLoginHolder.getUsername());
        if (metadata == null) return;
        try (InputStream inputStream = new FileInputStream(file);
             InputStream gzipInputStream = gzipCompress(inputStream, metadata)) {
            gridFsTemplate.store(gzipInputStream, fileDocument.getId(), metadata);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void saveFileVersion(AbstractOssObject abstractOssObject, String fileId) {
        long size = abstractOssObject.getContentLength();
        String filename = Paths.get(abstractOssObject.getKey()).getFileName().toString();
        String updateDate = DateUtil.format(abstractOssObject.getFileInfo().getLastModified(), DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN));
        Metadata metadata = setMetadata(size, fileId, filename, updateDate, userLoginHolder.getUsername());
        if (metadata == null) return;
        try (InputStream inputStream = abstractOssObject.getInputStream();
             InputStream gzipInputStream = gzipCompress(inputStream, metadata)) {
            gridFsTemplate.store(gzipInputStream, fileId, metadata);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
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
    private static Metadata setMetadata(long size, String filepath, String filename, String updateDate, String username) {
        Metadata metadata = new Metadata();
        metadata.setFilepath(filepath);
        metadata.setFilename(filename);
        metadata.setTime(updateDate);
        metadata.setSize(size);
        metadata.setOperator(username);
        if (size == 0) {
            // 无内容，不用存历史版本
            return null;
        }
        if (size >= 1024) {
            metadata.setCompression("gzip");
        }
        return metadata;
    }

    /**
     * 读取文件
     *
     * @param gridFSId GridFSId
     * @return InputStream
     */
    public InputStream readFileVersion(String gridFSId) throws IOException {
        GridFSFile gridFSFile = getGridFSFile(gridFSId);
        if (gridFSFile != null) {
            return getInputStream(gridFSFile);
        }
        return null;
    }

    private InputStream getInputStream(GridFSFile gridFSFile) throws IOException {
        GridFsResource gridFsResource = gridFsTemplate.getResource(gridFSFile);
        return gzipDecompress(gridFsResource.getInputStream(), gridFSFile.getMetadata());
    }

    @Nullable
    private GridFSFile getGridFSFile(String gridFSId) {
        Query query = getQueryOfId(gridFSId);
        return gridFsTemplate.findOne(query);
    }

    @NotNull
    private static Query getQueryOfId(String gridFSId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(gridFSId));
        return query;
    }

    public ResponseResult<List<GridFSBO>> listFileVersion(String fileId, Integer pageSize, Integer pageIndex) {
        List<GridFSBO> gridFSBOList = new ArrayList<>();
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILENAME).is(fileId));
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        if (count == 0) {
            return ResultUtil.success(gridFSBOList).setCount(0);
        }
        CommonFileService.setPage(pageSize, pageIndex, query);
        query.with(Sort.by(Sort.Direction.DESC, Constants.UPLOAD_DATE));
        gridFSBOList = mongoTemplate.find(query, GridFSBO.class, COLLECTION_NAME);
        return ResultUtil.success(gridFSBOList).setCount(count);
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
            Path filePath = Paths.get(URLUtil.decode(path));
            String relativePath = File.separator;
            if (filePath.getNameCount() > 1) {
                relativePath += filePath.subpath(0, filePath.getNameCount() - 1) + File.separator;
            }
            String userId = userLoginHolder.getUserId();
            FileDocument fileDocument = commonFileService.getFileDocumentByPath(relativePath, filePath.getFileName().toString(), userId);
            if (fileDocument == null) {
                return ResultUtil.success(new ArrayList<>());
            }
            if (fileDocument.getId() != null) {
                fileId = fileDocument.getId();
            }
        }
        if (CharSequenceUtil.isBlank(fileId)) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        return listFileVersion(fileId, pageSize, pageIndex);
    }

    @Override
    public FileDocument getFileById(String gridFSId) {
        GridFSFile gridFSFile = getGridFSFile(gridFSId);
        if (gridFSFile == null || gridFSFile.getMetadata() == null) {
            return null;
        }
        String fileId = gridFSFile.getFilename();
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null) {
            return null;
        }
        fileDocument.setSize(gridFSFile.getLength());
        fileDocument.setName(gridFSFile.getMetadata().getString(Constants.FILENAME));
        Charset charset = getCharset(gridFSFile);
        try (InputStream inputStream = getInputStream(gridFSFile)) {
            String content = IOUtils.toString(inputStream, charset);
            fileDocument.setContentText(content);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return fileDocument;
    }

    private Charset getCharset(GridFSFile gridFSFile) {
        Charset charset = StandardCharsets.UTF_8;
        try (InputStream inputStream = getInputStream(gridFSFile)) {
            charset = CharsetDetector.detect(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return charset;
    }

    @Override
    public StreamingResponseBody getStreamFileById(String gridFSId) {
        return outputStream -> {
            GridFSFile gridFSFile = getGridFSFile(gridFSId);
            if (gridFSFile == null) {
                return;
            }
            Charset charset = getCharset(gridFSFile);
            try (InputStream inputStream = getInputStream(gridFSFile);
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream, charset);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    outputStream.write(line.getBytes(charset));
                    outputStream.write("\n".getBytes(charset));
                    outputStream.flush();
                }
            } catch (ClientAbortException ignored) {
                // ignored
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        };
    }

    @Override
    public void deleteAll(List<String> fileIds) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where(Constants.FILENAME).in(fileIds));
            gridFsTemplate.delete(query);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void deleteAll(String fileId) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where(Constants.FILENAME).is(fileId));
            gridFsTemplate.delete(query);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void deleteOne(String id) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(id));
            gridFsTemplate.delete(query);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void rename(String sourceFileId, String destinationFileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILENAME).is(sourceFileId));
        Update update = new Update();
        update.set(Constants.FILENAME, destinationFileId);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
    }

    @Override
    public Long recovery(String gridFSId) {
        GridFSFile gridFSFile = getGridFSFile(gridFSId);
        if (gridFSFile == null || gridFSFile.getMetadata() == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        String fileId = gridFSFile.getFilename();
        String filename = gridFSFile.getMetadata().getString(Constants.FILENAME);
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        String username = userLoginHolder.getUsername();
        if (CharSequenceUtil.isBlank(username)) {
            throw new CommonException(ExceptionType.LOGIN_EXCEPTION);
        }
        File file = Paths.get(fileProperties.getRootDir(), username, fileDocument.getPath(), filename).toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        if (CommonFileService.isLock(file, fileProperties.getRootDir(), username)) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }
        LocalDateTime time = LocalDateTimeUtil.now();
        try (InputStream inputStream = getInputStream(gridFSFile)) {
            FileUtil.writeFromStream(inputStream, file);
            Query query = new Query().addCriteria(Criteria.where("_id").is(fileId));
            Update update = new Update();
            update.set("updateDate", time);
            mongoTemplate.updateFirst(query, update, FileDocument.class);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Override
    public ResponseEntity<InputStreamResource> readHistoryFile(String gridFSId) {
        GridFSFile gridFSFile = getGridFSFile(gridFSId);
        if (gridFSFile == null || gridFSFile.getMetadata() == null) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream inputStream = getInputStream(gridFSFile)) {
            String filename = gridFSFile.getMetadata().getString(Constants.FILENAME);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + UriUtils.encode(filename, StandardCharsets.UTF_8));
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/octet-stream"))
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * gzip压缩
     *
     * @param inputStream 原始 inputStream
     * @param metadata    自定义元数据
     * @return 压缩后的 inputStream
     */
    public static InputStream gzipCompress(InputStream inputStream, Metadata metadata) {
        if (metadata != null && !"gzip".equals(metadata.getCompression())) {
            return inputStream;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(out)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                gzipOut.write(buffer, 0, len);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * gzip解压
     *
     * @param inputStream 压缩后的 inputStream
     * @param metadata    自定义元数据
     * @return 解压后的 inputStream
     */
    public static InputStream gzipDecompress(InputStream inputStream, Document metadata) {
        if (metadata != null && !"gzip".equals(metadata.get("compression"))) {
            return inputStream;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(inputStream)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

}
