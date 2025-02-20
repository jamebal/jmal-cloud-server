package com.jmal.clouddisk.service;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.ResponseResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    /**
     * 获取文件信息
     * @param id 文件id
     * @return FileDocument
     */
    Optional<FileDocument> getById(String id, Boolean content);

    /**
     * 流式读取文本文件
     * @param id 文件id
     * @return StreamingResponseBody
     */
    StreamingResponseBody getStreamById(String id);

    FileDocument getFileDocumentByPathAndName(String path, String name, String username);

    /**
     * 根据path读取simText文件
     * @param path 文件路径
     * @param username 用户名
     * @return FileDocument
     *
     */
    FileDocument previewTextByPath(String path, String username);

    /**
     * 根据path流式读取simText文件
     * @param path 文件路径
     * @param username 用户名
     * @return StreamingResponseBody
     */
    StreamingResponseBody previewTextByPathStream(String path, String username);

    /**
     * 查找下级目录
     * @param upload 上传参数
     * @param fileId 文件id
     * @return ResponseResult<Object>
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

    /**
     * 上传文件
     * @param upload 上传参数
     * @return ResponseResult<Object>
     * @throws IOException IOException
     */
    ResponseResult<Object> upload(UploadApiParamDTO upload) throws IOException;

    /**
     * 上传文件夹
     * @param upload 上传参数
     * @return ResponseResult<Object>
     * @throws CommonException CommonException
     */
    ResponseResult<Object> uploadFolder(UploadApiParamDTO upload);

    /**
     * 新建文件夹
     * @param upload 上传参数
     * @return ResponseResult<Object>
     * @throws CommonException CommonException
     */
    ResponseResult<Object> newFolder(UploadApiParamDTO upload);

    /**
     * 检查文件/分片是否存在
     * @param upload 上传参数
     * @return ResponseResult<Object>
     * @throws IOException IOException
     */
    ResponseResult<Object> checkChunkUploaded(UploadApiParamDTO upload) throws IOException;

    /**
     * 合并文件
     * @param upload 上传参数
     * @return ResponseResult<Object>
     * @throws IOException IOException
     */
    ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException;

    /**
     * 文件列表
     * @param upload 上传参数
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> listFiles(UploadApiParamDTO upload);

    /**
     * 用户占用空间
     * @param userId 用户id
     * @return long
     */
    long takeUpSpace(String userId);

    /**
     * 搜索文件
     * @param upload 上传参数
     * @param keyword 关键字
     * @return ResponseResult<Object>
     */
    ResponseResult<List<FileIntroVO>> searchFile(UploadApiParamDTO upload, String keyword);

    /**
     * 搜索文件并打开文件夹
     * @param upload 上传参数
     * @param id 文件id
     * @param folder 父级文件夹fileId
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id, String folder);

    /**
     * 收藏文件或文件夹
     * @param fileIds 文件id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> favorite(List<String> fileIds);

    /**
     * 取消收藏
     * @param fileIds 文件id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> unFavorite(List<String> fileIds);

    /**
     * 删除
     * @param username 用户名
     * @param currentDirectory 当前目录
     * @param fileIds 文件id
     * @param operator 操作者
     * @param sweep 是否彻底删除
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delete(String username, String currentDirectory, List<String> fileIds, String operator, boolean sweep);

    /**
     * 显示缩略图
     *
     * @param id        fileId
     * @param showCover 是否显示封面
     * @return FileDocument
     */
    Optional<FileDocument> thumbnail(String id, Boolean showCover);

    /**
     * 获取dwg文件对应的mxweb文件
     * @param id fileId
     * @return FileDocument
     */
    Optional<FileDocument> getMxweb(String id);

    /**
     * 显示缩略图(媒体文件封面)
     * @param id fileId
     * @param username username
     * @return FileDocument
     */
    Optional<FileDocument> coverOfMedia(String id, String username);

    ResponseEntity<Object> getObjectResponseEntity(FileDocument fileDocument);

    /**
     * 分享里的打包下载
     * @param request 请求
     * @param response 响应
     * @param fileIdList 文件id列表
     */
    void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList);

    /**
     * 打包下载
     * @param request 请求
     * @param response 响应
     * @param fileIdList 文件id列表
     */
    void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList);

    /**
     * 重名名
     * @param newFileName 新文件名
     * @param username 用户名
     * @param id     文件id
     * folder 父级文件夹fileId
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> rename(String newFileName, String username, String id, String folder);

    /**
     * 移动或复制前检查目标目录是否存在要移动或复制的文件
     * @param upload 上传参数
     * @param froms 从哪里来
     * @param to    要到哪里去
     * @return ResponseResult<Object>
     */
    ResponseResult<List<FileDocument>> checkMoveOrCopy(UploadApiParamDTO upload, List<String> froms, String to) throws IOException;

    /**
     * 移动文件/文件夹
     * @param upload 上传参数
     * @param froms 从哪里移动
     * @param to   移动到哪里
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> move(UploadApiParamDTO upload, List<String> froms, String to) throws IOException;

    /**
     * 复制文件/文件夹
     * @param upload 上传参数
     * @param froms 从哪里复制
     * @param to  复制到哪里
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> copy(UploadApiParamDTO upload, List<String> froms, String to) throws IOException;

    /**
     * 上传用户图片
     * @param upload 上传参数
     * @return String
     */
    String uploadConsumerImage(UploadApiParamDTO upload);

    /**
     * 根据文件Id获取文件信息
     * @param fileId 文件Id
     * @return FileDocument
     */
    FileDocument getById(String fileId);

    /**
     * 创建文件/文件夹(mongodb)
     * @param username 用户名
     * @param file 文件/文件夹
     * @return 文件/文件夹id
     */
    String createFile(String username, File file);

    /**
     * 修改文件/文件夹
     *
     * @param username 用户名
     * @param file     文件/文件夹
     */
    void updateFile(String username, File file);

    /**
     * 删除文件/文件夹(mongodb)
     * @param username  用户名
     * @param file 文件/文件夹
     */
    void deleteFile(String username, File file);

    /**
     * 解压zip文件
     * @param fileId 文件id
     * @param destFileId 目标文件id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> unzip(String fileId, String destFileId);

    /**
     * 获取目录下的文件
     * @param path 文件目录路径
     * @param username 用户名
     * @param tempDir 是否为零时目录
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> listFiles(String path, String username, boolean tempDir);

    /**
     * 获取上级文件列表
     * @param path 文件目录路径
     * @param username 用户名
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> upperLevelList(String path, String username);

    /**
     * 根据path删除文件/文件夹
     * @param path 文件/文件夹路径
     * @param username 用户名
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> delFile(String path, String username);

    /**
     * 根据path重命名
     * @param newFileName 新文件名
     * @param username 用户名
     * @param path 文件/文件夹路径
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> renameByPath(String newFileName, String username, String path);

    /**
     * 根据path添加文件
     * @param fileName 文件名
     * @param isFolder 是否为文件夹
     * @param username 用户名
     * @param parentPath 父目录路径
     * @param folder 父级文件夹fileId
     * @return ResponseResult<FileIntroVO>
     */
    ResponseResult<FileIntroVO> addFile(String fileName, Boolean isFolder, String username, String parentPath, String folder);

    /**
     * 下载、预览文件
     * @param shareKey 分享文件id
     * @param fileId 当前文件Id
     * @param operation 操作(下载、预览等操作)
     * @return forward路径
     */
    String viewFile(String shareKey, String fileId, String shareToken, String operation);

    /**
     * 预览文档里的图片
     * @param relativePath 相对路径
     * @param userId 用户id
     * @return forward路径
     */
    String publicViewFile(String relativePath, String userId);

    /***
     * 删除用户的所有文件
     * @param userList 用户列表
     */
    void deleteAllByUser(List<ConsumerDO> userList);

    /***
     * 设置文件为分享文件
     * @param file FileDocument
     * @param expiresAt 过期时间
     * @param share share
     */
    void setShareFile(FileDocument file, long expiresAt, ShareDO share);

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

    /**
     * 根据文件Id列表获取文件列表
     * @param fileIdList 文件Id列表
     * @return 文件列表
     */
    List<FileDocument> listByIds(List<String> fileIdList);

    /**
     * 创建副本
     * @param fileId 文件id
     * @param newFilename 新文件名(包含后缀)
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> duplicate(String fileId, String newFilename);

    /**
     * 设置标签
     * @param editTagDTO EditTagDTO
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> setTag(EditTagDTO editTagDTO);

    /**
     * 修改标签颜色或名称
     * @param tagId 标签id
     * @param tagName 标签名
     * @param color 标签颜色
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> setTag(String tagId, String tagName, String color);

    /**
     * 删除文件标签
     * @param tagId 标签id
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> deleteTag(String tagId);

    /**
     * 返回原处
     * @param fileIds 文件id列表
     * @param username 用户名
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> restore(List<String> fileIds, String username);

    /**
     * 清空回收站
     * @param username 用户名
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> clearTrash(String username);

    /**
     * 彻底删除
     * @param fileIds 文件id列表
     * @param username 用户名
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> sweep(List<String> fileIds, String username);

    /**
     * 是否允许下载
     * @param fileIds 文件id列表
     * @return ResponseResult<Object>
     */
    ResponseResult<Object> isAllowDownload(List<String> fileIds);
}
