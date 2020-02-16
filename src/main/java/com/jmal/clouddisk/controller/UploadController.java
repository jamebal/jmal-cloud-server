package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.AuthInterceptor;
import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.common.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.TimeUnit;


/**
 * @Description 云文件管理控制器
 * @Author jmal
 * @Date 2020-01-27 12:59
 * @blame jmal
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class UploadController {

    @Autowired
    private IUploadFileService fileService;

    @Autowired
    IUserService service;

    @Value("${root-path}")
    String rootPath;

    /***
     * 文件列表
     * @param upload
     * @param pageIndex
     * @param pageSize
     * @return
     * @throws CommonException
     */
    @GetMapping("/list")
    public ResponseResult list(UploadApiParam upload, int pageIndex, int pageSize) throws CommonException {
        return fileService.listFiles(upload, pageIndex, pageSize);
    }

    /***
     * 搜索文件
     * @param upload
     * @return
     * @throws CommonException
     */
    @GetMapping("/search-file")
    public ResponseResult searchFile(UploadApiParam upload, String keyword, int pageIndex, int pageSize) throws CommonException {
        return fileService.searchFile(upload, keyword, pageIndex, pageSize);
    }

    /***
     * 搜索文件并打开文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    @GetMapping("/search-file-open")
    public ResponseResult searchFileAndOpenDir(UploadApiParam upload, String id, int pageIndex, int pageSize) throws CommonException {
        return fileService.searchFileAndOpenDir(upload, id, pageIndex, pageSize);
    }

    /***
     * 文件上传
     * @param upload
     * @return
     * @throws IOException
     */
    @PostMapping("upload")
    @ResponseBody
    public ResponseResult uploadPost(UploadApiParam upload) throws IOException {
        upload.setRootPath(rootPath);
        System.out.println("upload:" + upload.toString());
        return fileService.upload(upload);
    }

    /***
     * 文件夹上传
     * @param upload
     * @return
     */
    @PostMapping("upload-folder")
    @ResponseBody
    public ResponseResult uploadFolder(UploadApiParam upload) throws CommonException {
        upload.setRootPath(rootPath);
        System.out.println("upload-folder:" + upload.toString());
        return fileService.uploadFolder(upload);
    }

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     */
    @GetMapping("upload")
    @ResponseBody
    public ResponseResult checkUpload(UploadApiParam upload) throws IOException {
        upload.setRootPath(rootPath);
        System.out.println("check:" + upload.toString());
        return fileService.checkChunkUploaded(upload);
    }

    /***
     * 合并文件
     * @param upload
     * @return
     * @throws IOException
     */
    @PostMapping("merge")
    @ResponseBody
    public ResponseResult merge(UploadApiParam upload) throws IOException {
        upload.setRootPath(rootPath);
        System.out.println("merge:" + upload.toString());
        return fileService.merge(upload);
    }

    /**
     * 在线显示文件
     * @param id 文件id
     * @return
     */
    @GetMapping("/view")
    public ResponseEntity<Object> serveFileOnline(HttpServletRequest request, String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.getById(id, service.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN)));
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getSize() + "").header("Connection", "close")
                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getSize() + "")
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    /**
     * 预览文件
     * @param fileIds fileIds
     * @return
     */
    @GetMapping("/preview/{filename}")
    public void preview(HttpServletRequest request, HttpServletResponse response, String[] fileIds,@PathVariable String filename) throws IOException {
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
    @GetMapping("/download")
    public void downLoad(HttpServletRequest request, HttpServletResponse response, String[] fileIds) throws IOException {
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
    @PostMapping("/favorite")
    public ResponseResult favorite(HttpServletRequest request, String id) throws CommonException {
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
    @PostMapping("/unFavorite")
    public ResponseResult unFavorite(HttpServletRequest request, String id) throws CommonException {
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
    @DeleteMapping("/delete")
    public ResponseResult delete(String username, String[] fileIds) throws CommonException {
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
    @GetMapping("/rename")
    public ResponseResult rename(String newFileName, String username, String id) throws CommonException {
        return fileService.rename(newFileName, username, id);
    }

}
