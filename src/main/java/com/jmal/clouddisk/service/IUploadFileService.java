package com.jmal.clouddisk.service;

import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.util.ResponseResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * IUploadFileService
 *
 * @blame jmal
 */
public interface IUploadFileService {

    /***
     * 在线显示文件
     * @param id
     * @param username
     * @return
     */
    Optional<FileDocument> getById(String id, String username);

    /***
     * 查找下级目录
     * @param upload
     * @param fileId
     * @return
     */
    ResponseResult<Object> queryFileTree(UploadApiParam upload, String fileId);

    /***
     * 上传文件
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult<Object> upload(UploadApiParam upload) throws IOException;

    /***
     * 上传文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> uploadFolder(UploadApiParam upload) throws CommonException;

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult<Object> checkChunkUploaded(UploadApiParam upload) throws IOException;

    /***
     * 合并文件
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult<Object> merge(UploadApiParam upload) throws IOException;

    /***
     * 文件列表
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> listFiles(UploadApiParam upload) throws CommonException;

    /***
     * 搜索文件
     * @param upload
     * @param keyword
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> searchFile(UploadApiParam upload, String keyword) throws CommonException;

    /***
     * 搜索文件并打开文件夹
     * @param upload
     * @param id
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> searchFileAndOpenDir(UploadApiParam upload, String id) throws CommonException;

    /***
     * 收藏文件或文件夹
     * @param fileId
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> favorite(String fileId) throws CommonException;

    /***
     * 取消收藏
     * @param id
     * @return
     */
    ResponseResult<Object> unFavorite(String id);

    /***
     * 删除
     * @param username
     * @param fileIds
     * @return
     */
    ResponseResult<Object> delete(String username, List<String> fileIds);

    /***
     * 显示缩略图
     * @param id
     * @param userName
     * @return
     * @throws IOException
     */
    Optional<FileDocument> thumbnail(String id, String userName) throws IOException;

    /***
     * 转给Nginx处理
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws IOException
     */
    void nginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws IOException;

    /***
     * 重名名
     * @param newFileName
     * @param username
     * @param id
     * @return
     */
    ResponseResult<Object> rename(String newFileName, String username, String id);

    /***
     * 移动文件/文件夹
     * @param upload
     * @param froms
     * @param to
     * @return
     */
    ResponseResult move(UploadApiParam upload, List<String> froms, String to);

    /***
     * 复制文件/文件夹
     * @param upload
     * @param froms
     * @param to
     * @return
     */
    ResponseResult copy(UploadApiParam upload, List<String> froms, String to);

    /***
     * 获取markdown内容
     * @param mark
     * @return
     */
    ResponseResult<Object> getMarkDownContent(String mark);

    /***
     * 新建文档
     * @param upload
     * @return
     */
    ResponseResult<Object> newMarkdown(UploadApiParam upload);
}
