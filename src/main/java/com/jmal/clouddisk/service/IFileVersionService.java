package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.util.ResponseResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author jmal
 * @Description 文件版本管理
 * @date 2023/5/9 17:52
 */
public interface IFileVersionService {

    /**
     * 保存当前文件的为历史文件
     * @param username username
     * @param relativePath 文件的相对路径
     * @param userId userId
     */
    void saveFileVersion(String username, String relativePath, String userId);

    /**
     * 读取文件
     * @param id GridFSId
     * @return InputStream
     */
    InputStream readFileVersion(String id) throws IOException;

    /**
     * 列出文件的历史版本
     * @param fileId 文件id
     * @param pageSize 每页条数
     * @param pageIndex 页数
     * @return ResponseResult<List<GridFSBO>>
     */
    ResponseResult<List<GridFSBO>> listFileVersion(String fileId, Integer pageSize, Integer pageIndex);

    /**
     * 获取历史文件信息
     * @param fileId fileId
     * @return FileDocument
     */
    FileDocument getFileById(String fileId);

    /**
     * 流式读取历史simText文件
     * @param fileId fileId
     * @return StreamingResponseBody
     */
    StreamingResponseBody getStreamFileById(String fileId);
}
