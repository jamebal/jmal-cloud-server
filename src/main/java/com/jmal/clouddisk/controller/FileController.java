package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


/**
 * @Description 云文件管理控制器
 * @Author jmal
 * @Date 2020-01-27 12:59
 * @blame jmal
 */
@Api(tags = "文件管理")
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class FileController {

    @Autowired
    private IFileService fileService;

    @Autowired
    IUserService service;

    /***
     * 文件列表
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("文件列表")
    @GetMapping("/list")
    public ResponseResult<Object> list(UploadApiParam upload) throws CommonException {
        return fileService.listFiles(upload);
    }

    /***
     * 查找下级目录
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("查找下级目录")
    @GetMapping("/query-file-tree")
    public ResponseResult<Object> queryFileTree(UploadApiParam upload, String fileId) throws CommonException {
        return fileService.queryFileTree(upload,fileId);
    }

    /***
     * 搜索文件
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("搜索文件")
    @GetMapping("/search-file")
    public ResponseResult<Object> searchFile(UploadApiParam upload, String keyword) throws CommonException {
        return fileService.searchFile(upload, keyword);
    }

    /***
     * 搜索文件并打开文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("搜索文件并打开文件夹")
    @GetMapping("/search-file-open")
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParam upload, String id) throws CommonException {
        return fileService.searchFileAndOpenDir(upload, id);
    }

    /***
     * 文件上传
     * @param upload
     * @return
     * @throws IOException
     */
    @ApiOperation("文件上传")
    @PostMapping("upload")
    @ResponseBody
    public ResponseResult<Object> uploadPost(UploadApiParam upload) throws IOException {
        System.out.println("upload:" + upload.toString());
        return fileService.upload(upload);
    }

    /***
     * 文件夹上传
     * @param upload
     * @return
     */
    @ApiOperation("文件夹上传")
    @PostMapping("upload-folder")
    @ResponseBody
    public ResponseResult<Object> uploadFolder(UploadApiParam upload) throws CommonException {
        System.out.println("upload-folder:" + upload.toString());
        return fileService.uploadFolder(upload);
    }

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     */
    @ApiOperation("检查文件/分片是否存在")
    @GetMapping("upload")
    @ResponseBody
    public ResponseResult<Object> checkUpload(UploadApiParam upload) throws IOException {
        System.out.println("check:" + upload.toString());
        return fileService.checkChunkUploaded(upload);
    }

    /***
     * 合并文件
     * @param upload
     * @return
     * @throws IOException
     */
    @ApiOperation("合并文件")
    @PostMapping("merge")
    @ResponseBody
    public ResponseResult<Object> merge(UploadApiParam upload) throws IOException {
        System.out.println("merge:" + upload.toString());
        return fileService.merge(upload);
    }

//    /**
//     * 在线显示文件
//     * @param id 文件id
//     * @return
//     */
//    @ApiOperation("在线显示文件")
//    @GetMapping("/view")
//    public ResponseEntity<Object> serveFileOnline(String id,String username) {
//        ResultUtil.checkParamIsNull(id,username);
//        Optional<FileDocument> file = fileService.getById(id, username);
//        return file.<ResponseEntity<Object>>map(fileDocument ->
//                ResponseEntity.ok()
//                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
//                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
//                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getSize() + "").header("Connection", "close")
//                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getSize() + "")
//                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
//                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
//    }

    /***
     * 读取simText文件
     * @param id 文件id
     * @param username
     * @return
     * @throws CommonException
     */
    @ApiOperation("读取simText文件")
    @GetMapping("/preview/text")
    public ResponseResult<Object> preview(String id,String username) throws CommonException {
        ResultUtil.checkParamIsNull(id,username);
        return ResultUtil.success(fileService.getById(id, username));
    }

    /**
     * 预览文件
     * @param fileIds fileIds
     * @return
     */
    @ApiOperation("预览文件")
    @GetMapping("/preview/{filename}")
    public void preview(HttpServletRequest request, HttpServletResponse response, String[] fileIds,@PathVariable String filename) throws CommonException {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            fileService.nginx(request, response, list, false);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    /**
     * 下载文件 转到 Nginx 下载
     * @param fileIds
     * @return
     */
    @ApiOperation("下载文件 转到 Nginx 下载")
    @GetMapping("/download")
    public void downLoad(HttpServletRequest request, HttpServletResponse response, String[] fileIds) throws CommonException {
        System.out.println("download...");
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            fileService.nginx(request, response, list, true);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    /**
     * 显示缩略图
     *
     * @param id 文件id
     * @return
     */
    @ApiOperation("显示缩略图")
    @GetMapping("/view/thumbnail")
    public ResponseEntity<Object> thumbnail(HttpServletRequest request, String id) throws IOException {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id, service.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN)));
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getContent().length + "").header("Connection", "close")
                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getContent().length + "")
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    /***
     * 收藏文件或文件夹
     * @param request
     * @param id 文件id
     * @return
     * @throws CommonException
     */
    @ApiOperation("收藏文件或文件夹")
    @PostMapping("/favorite")
    public ResponseResult<Object> favorite(HttpServletRequest request, String id) throws CommonException {
        ResultUtil.checkParamIsNull(id);
        service.getUserName(request.getHeader(AuthInterceptor.JMAL_TOKEN));
        return fileService.favorite(id);
    }

    /***
     * 取消收藏
     * @param request
     * @param id 文件id
     * @return
     * @throws CommonException
     */
    @ApiOperation("取消收藏")
    @PostMapping("/unFavorite")
    public ResponseResult<Object> unFavorite(HttpServletRequest request, String id) throws CommonException {
        ResultUtil.checkParamIsNull(id);
        service.getUserName(request.getHeader(AuthInterceptor.JMAL_TOKEN));
        return fileService.unFavorite(id);
    }

    /***
     * 删除文件
     * @param username
     * @param fileIds 文件id
     * @return
     * @throws CommonException
     */
    @ApiOperation("删除文件")
    @DeleteMapping("/delete")
    public ResponseResult<Object> delete(String username, String[] fileIds) throws CommonException {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.delete(username, list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    /***
     * 重命名
     * @param newFileName
     * @param username
     * @param id
     * @return
     * @throws CommonException
     */
    @ApiOperation("重命名")
    @GetMapping("/rename")
    public ResponseResult<Object> rename(String newFileName, String username, String id) throws CommonException {
        return fileService.rename(newFileName, username, id);
    }

    /***
     * 移动文件/文件夹
     * @param upload
     * @param froms 文件/文件夹id
     * @param to 文件夹id
     * @return
     * @throws CommonException
     */
    @ApiOperation("移动文件/文件夹")
    @GetMapping("/move")
    public ResponseResult move(UploadApiParam upload, String[] froms, String to) throws CommonException {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.move(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    /***
     * 复制文件/文件夹
     * @param upload
     * @param froms 文件/文件夹id
     * @param to 文件夹id
     * @return
     * @throws CommonException
     */
    @ApiOperation("复制文件/文件夹")
    @GetMapping("/copy")
    public ResponseResult copy(UploadApiParam upload, String[] froms, String to) throws CommonException {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.copy(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }
}
