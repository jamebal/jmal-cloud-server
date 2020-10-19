package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
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
 * @author jmal
 */
@Api(tags = "文件管理")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class FileController {

    @Autowired
    private IFileService fileService;

    @Autowired
    IUserService service;

    @ApiOperation("文件列表")
    @GetMapping("/list")
    public ResponseResult<Object> list(UploadApiParamDTO upload) {
        return fileService.listFiles(upload);
    }

    @ApiOperation("查找下级目录")
    @GetMapping("/query-file-tree")
    public ResponseResult<Object> queryFileTree(UploadApiParamDTO upload, String fileId) {
        return fileService.queryFileTree(upload,fileId);
    }

    @ApiOperation("搜索文件")
    @GetMapping("/search-file")
    public ResponseResult<Object> searchFile(UploadApiParamDTO upload, String keyword) {
        return fileService.searchFile(upload, keyword);
    }

    @ApiOperation("搜索文件并打开文件夹")
    @GetMapping("/search-file-open")
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id) {
        return fileService.searchFileAndOpenDir(upload, id);
    }

    @ApiOperation("文件上传")
    @PostMapping("upload")
    @ResponseBody
    public ResponseResult<Object> uploadPost(UploadApiParamDTO upload) throws IOException {
        return fileService.upload(upload);
    }

    @ApiOperation("文件夹上传")
    @PostMapping("upload-folder")
    @ResponseBody
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) {
        return fileService.uploadFolder(upload);
    }

    @ApiOperation("新建文件夹")
    @PostMapping("new_folder")
    @ResponseBody
    public ResponseResult<Object> newFolder(UploadApiParamDTO upload) {
        return fileService.newFolder(upload);
    }

    @ApiOperation("检查文件/分片是否存在")
    @GetMapping("upload")
    @ResponseBody
    public ResponseResult<Object> checkUpload(UploadApiParamDTO upload) throws IOException {
        return fileService.checkChunkUploaded(upload);
    }

    @ApiOperation("合并文件")
    @PostMapping("merge")
    @ResponseBody
    public ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException {
        return fileService.merge(upload);
    }

    @ApiOperation("读取simText文件")
    @GetMapping("/preview/text")
    public ResponseResult<Object> previewText(@RequestParam String id, @RequestParam String username) {
        ResultUtil.checkParamIsNull(id,username);
        return ResultUtil.success(fileService.getById(id, username));
    }

    @ApiOperation("根据path读取simText文件")
    @GetMapping("/preview/path/text")
    public ResponseResult<Object> previewTextByPath(@RequestParam String path,@RequestParam String username) {
        return fileService.previewTextByPath(path, username);
    }

    @ApiOperation("打包下载")
    @GetMapping("/packageDownload")
    public void packageDownload(HttpServletRequest request, HttpServletResponse response, String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> fileIdList = Arrays.asList(fileIds);
            fileService.packageDownload(request, response, fileIdList);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @ApiOperation("显示缩略图")
    @GetMapping("/view/thumbnail")
    public ResponseEntity<Object> thumbnail(HttpServletRequest request, String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id, service.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN)));
        return getObjectResponseEntity(file);
    }

    private ResponseEntity<Object> getObjectResponseEntity(Optional<FileDocument> file) {
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent().length))
                        .header("Connection", "close")
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @ApiOperation("显示缩略图(mp3封面)")
    @GetMapping("/view/cover")
    public ResponseEntity<Object> coverOfMp3(HttpServletRequest request, String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.coverOfMp3(id, service.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN)));
        return getObjectResponseEntity(file);
    }

    @ApiOperation("收藏文件或文件夹")
    @PostMapping("/favorite")
    public ResponseResult<Object> favorite(String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.favorite(list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @ApiOperation("取消收藏")
    @PostMapping("/unFavorite")
    public ResponseResult<Object> unFavorite(String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.unFavorite(list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @ApiOperation("删除文件")
    @DeleteMapping("/delete")
    public ResponseResult<Object> delete(String username, String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.delete(username, list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @ApiOperation("重命名")
    @GetMapping("/rename")
    public ResponseResult<Object> rename(String newFileName, String username, String id) {
        return fileService.rename(newFileName, username, id);
    }

    @ApiOperation("移动文件/文件夹")
    @GetMapping("/move")
    public ResponseResult move(UploadApiParamDTO upload, String[] froms, String to) {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.move(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @ApiOperation("复制文件/文件夹")
    @GetMapping("/copy")
    public ResponseResult copy(UploadApiParamDTO upload, String[] froms, String to) {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.copy(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @ApiOperation("解压zip文件")
    @GetMapping("/unzip")
    public ResponseResult unzip(@RequestParam String fileId, String destFileId) {
        return fileService.unzip(fileId, destFileId);
    }

    @ApiOperation("获取目录下的文件")
    @GetMapping("/listfiles")
    public ResponseResult listFiles(@RequestParam String path, @RequestParam String username, Boolean tempDir) {
        Boolean dir = tempDir;
        if(dir == null){
            dir = false;
        }
        return fileService.listFiles(path, username, dir);
    }

    @ApiOperation("获取上级文件列表")
    @GetMapping("/upper-level-list")
    public ResponseResult upperLevelList(@RequestParam String path, @RequestParam String username) {
        return fileService.upperLevelList(path, username);
    }

    @ApiOperation("根据path删除文件/文件夹")
    @DeleteMapping("/delFile")
    public ResponseResult delFile(@RequestParam String path, @RequestParam String username) {
        return fileService.delFile(path, username);
    }

    @ApiOperation("根据path重命名")
    @GetMapping("/rename/path")
    public ResponseResult<Object> renameByPath(@RequestParam String newFileName,@RequestParam String username,@RequestParam String path) {
        return fileService.renameByPath(newFileName, username, path);
    }

    @ApiOperation("根据path添加文件/文件夹")
    @PostMapping("/addfile")
    public ResponseResult<Object> addFile(@RequestParam String fileName, @RequestParam Boolean isFolder, @RequestParam String username, @RequestParam String parentPath){
        return fileService.addFile(fileName, isFolder, username, parentPath);
    }
}
