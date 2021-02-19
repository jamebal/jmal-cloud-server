package com.jmal.clouddisk.service;

import cn.hutool.db.PageResult;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.ResponseResult;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * IFileService
 *
 * @author jmal
 */
public interface IFileService {

    /***
     * 在线显示文件
     * @param id
     * @param username
     * @return
     */
    Optional<Object> getById(String id, String username);

    FileDocument getFileDocumentByPathAndName(String path, String name, String username);

    /***
     * 根据path读取simText文件
     * @param path
     * @param username
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> previewTextByPath(String path, String username);

    /***
     * 查找下级目录
     * @param upload
     * @param fileId
     * @return
     */
    ResponseResult<Object> queryFileTree(UploadApiParamDTO upload, String fileId);

    /**
     * 上传图片
     * @param file 文件
     * @param baseUrl baseUrl
     * @param filepath 文件要存放的相对路径
     * @return String
     */
    String imgUpload(String baseUrl, String filepath, MultipartFile file);

    /***
     * 上传文件
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult<Object> upload(UploadApiParamDTO upload) throws IOException;

    /***
     * 上传文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> uploadFolder(UploadApiParamDTO upload);

    /***
     * 新建文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> newFolder(UploadApiParamDTO upload);

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult<Object> checkChunkUploaded(UploadApiParamDTO upload) throws IOException;

    /***
     * 合并文件
     * @param upload
     * @return
     * @throws IOException
     */
    ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException;

    /***
     * 文件列表
     * @param upload
     * @return
     */
    ResponseResult<Object> listFiles(UploadApiParamDTO upload);

    /***
     * 用户占用空间
     * @param userId
     * @return
     */
    long takeUpSpace(String userId);

    /***
     * 搜索文件
     * @param upload
     * @param keyword
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> searchFile(UploadApiParamDTO upload, String keyword);

    /***
     * 搜索文件并打开文件夹
     * @param upload
     * @param id
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id);

    /***
     * 收藏文件或文件夹
     * @param fileIds
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> favorite(List<String> fileIds);

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
    Optional<FileDocument> thumbnail(String id, String userName);

    /***
     * 显示缩略图(mp3封面)
     * @param id
     * @param userName
     * @return
     */
    Optional<FileDocument> coverOfMp3(String id, String userName);

    /***
     * 分享里的打包下载
     * @param request
     * @param response
     * @param fileIdList
     */
    void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList);

    /***
     * 打包下载
     * @param request
     * @param response
     * @param fileIdList
     */
    void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList);

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
    ResponseResult<Object> move(UploadApiParamDTO upload, List<String> froms, String to);

    /***
     * 复制文件/文件夹
     * @param upload
     * @param froms
     * @param to
     * @return
     */
    ResponseResult<Object> copy(UploadApiParamDTO upload, List<String> froms, String to);

    /***
     * 上传用户图片
     * @param upload
     * @return
     * @throws CommonException
     */
    String uploadConsumerImage(UploadApiParamDTO upload);

    /***
     * 根据文件Id获取文件信息
     * @param fileId
     * @return
     */
    FileDocument getById(String fileId);

    /***
     * 创建文件/文件夹(mongodb)
     * @param username
     * @param file
     * @return
     */
    String createFile(String username, File file);

    /***
     * 修改文件/文件夹
     * @param username
     * @param file
     * @return
     */
    String updateFile(String username, File file);

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
    ResponseResult<Object> unzip(String fileId, String destFileId);

    /***
     * 获取目录下的文件
     * @param path 文件目录路径
     * @param username 用户名
     * @param tempDir 是否为零时目录
     * @return
     * @throws CommonException
     */
    ResponseResult<Object> listFiles(String path, String username, boolean tempDir);

    /***
     * 获取上级文件列表
     * @param path
     * @param username
     * @return
     */
    ResponseResult<Object> upperLevelList(String path, String username);

    /***
     * 根据path删除文件/文件夹
     * @param path
     * @param username
     * @return
     */
    ResponseResult<Object> delFile(String path, String username);

    /***
     * 根据path重命名
     * @param newFileName
     * @param username
     * @param path
     * @return
     */
    ResponseResult<Object> renameByPath(String newFileName, String username, String path);

    /***
     * 根据path添加文件
     * @param fileName
     * @param isFolder
     * @param username
     * @param parentPath
     * @return
     */
    ResponseResult<Object> addFile(String fileName, Boolean isFolder, String username, String parentPath);

    /***
     * 下载单个文件
     * @param fileId
     * @param operation 操作(下载、预览等操作)
     * @return
     */
    String viewFile(String fileId, String operation);

    /***
     * 预览文档里的图片
     * @param relativePath
     * @param userId
     * @return
     */
    String publicViewFile(String relativePath, String userId);

    /***
     * 删除用户的所有文件
     * @param userList
     */
    void deleteAllByUser(List<ConsumerDO> userList);

    /***
     * 设置文件为分享文件
     * @param file FileDocument
     * @param expiresAt 过期时间
     */
    void setShareFile(FileDocument file, long expiresAt);

    /***
     * 取消文件的分享状态
     * @param file FileDocument
     */
    void unsetShareFile(FileDocument file);

    /***
     * 设为公共文件
     * @param fileId 文件Id
     */
    void setPublic(String fileId);
}
