package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.util.ResponseResult;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
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
     * 保存当前文件为历史文件
     * @param username username
     * @param relativePath 文件的相对路径
     * @param userId userId
     */
    void saveFileVersion(String username, String relativePath, String userId);

    /**
     * 保存当前文件为历史文件
     *
     * @param abstractOssObject AbstractOssObject
     * @param fileId fileId
     */
    void saveFileVersion(AbstractOssObject abstractOssObject, String fileId);

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
     * 列出文件的历史版本(根据filepath)
     * @param path 文件相对路径
     * @param pageSize 每页条数
     * @param pageIndex 页数
     * @return ResponseResult<List<GridFSBO>>
     */
    ResponseResult<List<GridFSBO>> listFileVersionByPath(String path, Integer pageSize, Integer pageIndex);

    /**
     * 获取历史文件信息
     * @param gridFSId gridFSId
     * @return FileDocument
     */
    FileDocument getFileById(String gridFSId);

    /**
     * 流式读取历史simText文件
     * @param gridFSId gridFSId
     * @return StreamingResponseBody
     */
    StreamingResponseBody getStreamFileById(String gridFSId);

    /**
     * 删除fileId的所有历史文件
     * @param fileIds 文件id列表
     */
    void deleteAll(List<String> fileIds);

    /**
     * 删除该文件的所有历史文件
     * @param fileId 文件id列表
     */
    void deleteAll(String fileId);

    /**
     * 删除指定的历史文件
     * @param id 历史文件id
     */
    void deleteOne(String id);

    /**
     * 重命名后的需要修改历史文件中 filename
     * @param sourceFileId 修改前的filename
     * @param destinationFileId 修改后的filename
     */
    void rename(String sourceFileId, String destinationFileId);

    /**
     * 恢复该历史版本
     *
     * @param gridFSId gridFSId
     */
    Long recovery(String gridFSId);

    /**
     * readHistoryFile
     * @param id 历史文件id
     * @return ResponseEntity<InputStream>
     */
    ResponseEntity<InputStreamResource> readHistoryFile(String id);
}
