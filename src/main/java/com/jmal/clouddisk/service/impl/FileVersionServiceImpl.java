package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.CharsetDetector;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
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

    private final CommonFileService commonFileService;

    private final GridFsTemplate gridFsTemplate;

    private final MongoTemplate mongoTemplate;

    private final FileProperties fileProperties;

    public void saveFileVersion(String username, String relativePath, String userId) {
        File file = new File(Paths.get(fileProperties.getRootDir(), username, relativePath).toString());
        String filepath = Paths.get(username, relativePath).toString();
        FileDocument fileDocument = commonFileService.getFileDocumentByPath(filepath, userId);
        DBObject metadata = new BasicDBObject();
        metadata.put(Constants.FILE_PATH, filepath);
        metadata.put(Constants.FILENAME, file.getName());
        metadata.put("time", LocalDateTimeUtil.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)));
        try (InputStream inputStream = new FileInputStream(file);
             InputStream gzipInputStream = gzipCompress(inputStream)) {
            if (gzipInputStream != null) {
                gridFsTemplate.store(gzipInputStream, fileDocument.getId(), metadata);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public InputStream readFileVersion(String id) throws IOException {
        GridFSFile gridFSFile = getGridFSFile(id);
        if (gridFSFile != null) {
            return getInputStream(gridFSFile);
        }
        return null;
    }

    @Nullable
    private InputStream getInputStream(GridFSFile gridFSFile) throws IOException {
        GridFsResource gridFsResource = gridFsTemplate.getResource(gridFSFile);
        return gzipDecompress(gridFsResource.getInputStream());
    }

    @Nullable
    private GridFSFile getGridFSFile(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        return gridFsTemplate.findOne(query);
    }

    public ResponseResult<List<GridFSBO>> listFileVersion(String fileId, Integer pageSize, Integer pageIndex) {
        Query query = new Query();
        query.addCriteria(Criteria.where("filename").is(fileId));
        long count = mongoTemplate.count(query, "fs.files");
        CommonFileService.setPage(pageSize, pageIndex, query);
        query.with(Sort.by(Sort.Direction.DESC, "uploadDate"));
        List<GridFSBO> gridFSBOList = mongoTemplate.find(query, GridFSBO.class, "fs.files");
        return ResultUtil.success(gridFSBOList).setCount(count);
    }

    @Override
    public FileDocument getFileById(String fileId) {
        FileDocument fileDocument = mongoTemplate.findById(fileId, FileDocument.class);
        if (fileDocument == null) {
            return null;
        }
        GridFSFile gridFSFile = getGridFSFile(fileId);
        if (gridFSFile == null || gridFSFile.getMetadata() == null) {
            return null;
        }
        fileDocument.setSize(gridFSFile.getLength());
        fileDocument.setName(gridFSFile.getMetadata().getString(Constants.FILENAME));
        try (InputStream inputStream = getInputStream(gridFSFile)) {
            if (inputStream != null) {
                Charset charset = CharsetDetector.detect(inputStream, StandardCharsets.UTF_8);
                inputStream.reset();
                String content = IOUtils.toString(inputStream, charset);
                fileDocument.setContentText(content);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return fileDocument;
    }

    @Override
    public StreamingResponseBody getStreamFileById(String fileId) {
        return outputStream -> {
            InputStream inputStream = readFileVersion(fileId);
            if (inputStream == null) {
                return;
            }
            Charset charset = CharsetDetector.detect(inputStream, StandardCharsets.UTF_8);
            inputStream.reset();
            try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream, charset);
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

    /**
     * gzip压缩
     *
     * @param inputStream 原始 inputStream
     * @return 压缩后的 inputStream
     */
    public static InputStream gzipCompress(InputStream inputStream) {
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
     * @return 解压后的 inputStream
     */
    public static InputStream gzipDecompress(InputStream inputStream) {
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
