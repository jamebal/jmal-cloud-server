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
     * 上传文件
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult upload(UploadApiParam upload) throws IOException;

    /***
     * 上传文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult uploadFolder(UploadApiParam upload) throws CommonException;

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult checkChunkUploaded(UploadApiParam upload) throws IOException;

    /***
     * 合并文件
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult merge(UploadApiParam upload) throws IOException;

    /***
     * 文件列表
     * @param upload
     * @param pageIndex
     * @param pageSize
     * @return
     * @throws CommonException
     */
    ResponseResult listFiles(UploadApiParam upload, int pageIndex, int pageSize) throws CommonException;

    /***
     * 收藏文件或文件夹
     * @param fileId
     * @return
     * @throws CommonException
     */
    ResponseResult favorite(String fileId) throws CommonException;

    /***
     * 取消收藏
     * @param id
     * @return
     */
    ResponseResult unFavorite(String id);

    /***
     * 删除
     * @param username
     * @param fileIds
     * @return
     */
    ResponseResult delete(String username, List<String> fileIds);

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
    ResponseResult rename(String newFileName, String username, String id);
}
