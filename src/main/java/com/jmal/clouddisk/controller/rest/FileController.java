package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
@Tag(name = "文件管理")
@Slf4j
@RestController
public class FileController {

    @Autowired
    private IFileService fileService;

    @Autowired
    AuthInterceptor authInterceptor;

    @Autowired
    UserLoginHolder userLoginHolder;

    @Autowired
    IUserService service;

    @Operation(summary = "根据id获取文件信息")
    @GetMapping("/file_info/{id}")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<FileDocument> getFileById(@PathVariable String id) {
        return ResultUtil.success(fileService.getById(id));
    }

    @Operation(summary = "文件列表")
    @GetMapping("/list")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> list(UploadApiParamDTO upload) {
        return fileService.listFiles(upload);
    }

    @Operation(summary = "查找下级目录")
    @GetMapping("/query-file-tree")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> queryFileTree(UploadApiParamDTO upload, String fileId) {
        return fileService.queryFileTree(upload,fileId);
    }

    @Operation(summary = "搜索文件")
    @GetMapping("/search-file")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> searchFile(UploadApiParamDTO upload, String keyword) {
        return fileService.searchFile(upload, keyword);
    }

    @Operation(summary = "搜索文件并打开文件夹")
    @GetMapping("/search-file-open")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id) {
        return fileService.searchFileAndOpenDir(upload, id);
    }

    @Operation(summary = "图片上传(Typora自定义命令上传图片接口)")
    @PostMapping("/img-upload")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public String imgUpload(HttpServletRequest request, MultipartFile file) {
        if (file == null){
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少文件参数, file");
        }
        String filepath = request.getHeader("filepath");
        if (CharSequenceUtil.isBlank(filepath)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "headers里缺少参数, filepath: 远程目标文件夹, 例如: '/Image/Typora/Public/Images'");
        }
        String baseUrl = request.getHeader("baseurl");
        if (CharSequenceUtil.isBlank(baseUrl)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "headers里缺少参数, baseUrl: 远程服务器地址, 例如: 'https://www.jmal.top/api'");
        }
        return fileService.imgUpload(baseUrl, filepath, file);
    }

    @Operation(summary = "文件上传")
    @PostMapping("upload")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> uploadPost(UploadApiParamDTO upload) throws IOException {
        return fileService.upload(upload);
    }

    @Operation(summary = "文件夹上传")
    @PostMapping("upload-folder")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) {
        return fileService.uploadFolder(upload);
    }

    @Operation(summary = "新建文件夹")
    @PostMapping("new_folder")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> newFolder(UploadApiParamDTO upload) {
        return fileService.newFolder(upload);
    }

    @Operation(summary = "检查文件/分片是否存在")
    @GetMapping("upload")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> checkUpload(UploadApiParamDTO upload) throws IOException {
        return fileService.checkChunkUploaded(upload);
    }

    @Operation(summary = "合并文件")
    @PostMapping("merge")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException {
        return fileService.merge(upload);
    }

    @Operation(summary = "读取simText文件")
    @GetMapping("/preview/text")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Optional<FileDocument>> previewText(@RequestParam String id, @RequestParam String username) {
        ResultUtil.checkParamIsNull(id,username);
        return ResultUtil.success(fileService.getById(id, username));
    }

    @Operation(summary = "根据path读取simText文件")
    @GetMapping("/preview/path/text")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> previewTextByPath(@RequestParam String path,@RequestParam String username) {
        return fileService.previewTextByPath(URLUtil.decode(path), username);
    }

    @Operation(summary = "是否允许下载")
    @GetMapping("/isAllowDownload")
    @Permission("cloud:file:download")
    public ResponseResult<Object> isAllowDownload() {
        return ResultUtil.success(true);
    }

    @Operation(summary = "打包下载")
    @GetMapping("/packageDownload")
    @LogOperatingFun
    @Permission("cloud:file:download")
    public void packageDownload(HttpServletRequest request, HttpServletResponse response, @RequestParam String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> fileIdList = Arrays.asList(fileIds);
            fileService.packageDownload(request, response, fileIdList);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/view/thumbnail")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> thumbnail(String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id, userLoginHolder.getUsername());
        return getObjectResponseEntity(file);
    }

    private ResponseEntity<Object> getObjectResponseEntity(Optional<FileDocument> file) {
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONNECTION, "close")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent().length))
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @Operation(summary = "显示缩略图(mp3封面)")
    @GetMapping("/view/cover")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> coverOfMp3(HttpServletRequest request, String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.coverOfMp3(id, service.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN)));
        return getObjectResponseEntity(file);
    }

    @Operation(summary = "收藏文件或文件夹")
    @PostMapping("/favorite")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> favorite(@RequestParam String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.favorite(list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "设为公共文件")
    @PutMapping("/setPublic")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> setPublic(@RequestParam String fileId) {
        fileService.setPublic(fileId);
        return ResultUtil.success();
    }

    @Operation(summary = "取消收藏")
    @PostMapping("/unFavorite")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> unFavorite(@RequestParam String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.unFavorite(list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete")
    @LogOperatingFun
    @Permission("cloud:file:delete")
    public ResponseResult<Object> delete(String username, @RequestParam  String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.delete(username, list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "重命名")
    @GetMapping("/rename")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> rename(String newFileName, String username, String id) {
        return fileService.rename(URLUtil.decode(newFileName), username, id);
    }

    @Operation(summary = "移动文件/文件夹")
    @GetMapping("/move")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> move(UploadApiParamDTO upload, @RequestParam String[] froms, String to) {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.move(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "复制文件/文件夹")
    @GetMapping("/copy")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> copy(UploadApiParamDTO upload, @RequestParam String[] froms, String to) {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.copy(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "解压zip文件")
    @GetMapping("/unzip")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> unzip(@RequestParam String fileId, String destFileId) {
        return fileService.unzip(fileId, destFileId);
    }

    @Operation(summary = "获取目录下的文件")
    @GetMapping("/listfiles")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> listFiles(@RequestParam String path, @RequestParam String username, Boolean tempDir) {
        Boolean dir = tempDir;
        if(dir == null){
            dir = false;
        }
        return fileService.listFiles(URLUtil.decode(path), username, dir);
    }

    @Operation(summary = "获取上级文件列表")
    @GetMapping("/upper-level-list")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> upperLevelList(@RequestParam String path, @RequestParam String username) {
        return fileService.upperLevelList(URLUtil.decode(path), username);
    }

    @Operation(summary = "根据path删除文件/文件夹")
    @DeleteMapping("/delFile")
    @LogOperatingFun
    @Permission("cloud:file:delete")
    public ResponseResult<Object> delFile(@RequestParam String path, @RequestParam String username) {
        return fileService.delFile(URLUtil.decode(path), username);
    }

    @Operation(summary = "根据path重命名")
    @GetMapping("/rename/path")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> renameByPath(@RequestParam String newFileName,@RequestParam String username,@RequestParam String path) {
        return fileService.renameByPath(URLUtil.decode(newFileName), username, URLUtil.decode(path));
    }

    @Operation(summary = "根据path添加文件/文件夹")
    @PostMapping("/addfile")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<FileDocument> addFile(@RequestParam String fileName, @RequestParam Boolean isFolder, @RequestParam String username, @RequestParam String parentPath){
        return fileService.addFile(URLUtil.decode(fileName), isFolder, username, URLUtil.decode(parentPath));
    }
}
