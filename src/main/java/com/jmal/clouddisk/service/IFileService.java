package com.jmal.clouddisk.service;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.util.ResponseResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * IFileService
 *
 * @blame jmal
 */
public interface IFileService {

    /***
     * 在线显示文件
     * @param id
     * @param username
     * @return
     */
    Optional<Object> getById(String id, String username);

    /***
     * 根据path读取simText文件
     * @param path
     * @param username
     * @return
     */
    ResponseResult<Object> previewTextByPath(String path, String username) throws CommonException;

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
     * 新建文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> newFolder(UploadApiParam upload) throws CommonException;

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
     * 用户占用空间
     * @return
     * @throws CommonException
     */
    long takeUpSpace(String userId) throws CommonException;

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
     * @param fileIds
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> favorite(List<String> fileIds) throws CommonException;

    /***
     * 取消收藏
     * @param fileIds
     * @return
     */
    ResponseResult<Object> unFavorite(List<String> fileIds);

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
    Optional<FileDocument> thumbnail(String id, String userName) throws CommonException;

    /***
     * 显示缩略图(mp3封面)
     * @param id
     * @param userName
     * @return
     */
    Optional<FileDocument> coverOfMp3(String id, String userName) throws CommonException;

    /***
     * 转给Nginx处理
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws CommonException
     */
    void nginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws CommonException;

    /***
     * 转给Nginx处理(共有的,任何人都和访问)
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws CommonException
     */
    void publicNginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws CommonException;

    /***
     * 转给Nginx处理(共有的,任何人都和访问)
     * @param request
     * @param response
     * @param relativePath
     * @param userId
     * @throws CommonException
     */
    void publicNginx(HttpServletRequest request, HttpServletResponse response, String relativePath, String userId) throws CommonException;

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
    ResponseResult<Object> getMarkDownContent(String mark) throws CommonException;

    /***
     * 新建文档
     * @param upload
     * @return
     */
    ResponseResult<Object> newMarkdown(UploadApiParam upload);

    /***
     * 编辑文档
     * @param upload
     * @return
     */
    ResponseResult<Object> editMarkdown(UploadApiParam upload);

    /***
     * 编辑文档(根据path)
     * @param upload
     * @return
     */
    ResponseResult<Object> editMarkdownByPath(UploadApiParam upload);

    /***
     * 上传文档里的图片
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> uploadMarkdownImage(UploadApiParam upload) throws CommonException;

    /***
     * 上传用户图片
     * @param upload
     * @return
     * @throws CommonException
     */
    String uploadConsumerImage(UploadApiParam upload) throws CommonException;

    FileDocument getById(String fileId);

    /***
     * 创建文件/文件夹(mongodb)
     * @param username
     * @param file
     */
    void createFile(String username, File file);

    /***
     * 修改文件/文件夹
     * @param username
     * @param file
     */
    void updateFile(String username, File file);

    /***
     * 删除文件/文件夹(mongodb)
     * @param username
     * @param file
     */
    void deleteFile(String username, File file);

    /***
     * 解压zip文件
     * @param fileId
     * @param destFileId
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> unzip(String fileId, String destFileId) throws CommonException;

    /***
     * 获取目录下的文件
     * @param path
     * @param username
     * @param tempDir
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> listfiles(String path, String username, boolean tempDir) throws CommonException;

    /***
     * 获取上级文件列表
     * @param path
     * @param username
     * @return
     */
    ResponseResult upperLevelList(String path, String username);

    /***
     * 根据path删除文件/文件夹
     * @param path
     * @param username
     * @return
     */
    ResponseResult delFile(String path, String username) throws CommonException;
}
