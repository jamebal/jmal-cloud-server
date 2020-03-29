package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IFileService;
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
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @Description share
 * @Author jmal
 * @Date 2020-03-17 16:22
 */
@Api(tags = "文件分享")
@RestController
public class ShareController {

    @Autowired
    IShareService shareService;

    @Autowired
    IFileService fileService;

    /***
     * 生成分享链接
     * @param share
     * @return
     * @throws CommonException
     */
    @ApiOperation("生成分享链接")
    @PostMapping("/share/generate")
    @ResponseBody
    public ResponseResult<Object> generateLink(@RequestBody ShareBO share) throws CommonException {
        ResultUtil.checkParamIsNull(share.getFileId(), share.getUserId());
        return shareService.generateLink(share);
    }

    /***
     * 取消分享
     * @param share
     * @return
     * @throws CommonException
     */
    @ApiOperation("取消分享")
    @DeleteMapping("/share/cancel")
    @ResponseBody
    public ResponseResult<Object> cancelShare(String[] shareId,String userId) throws CommonException {
        ResultUtil.checkParamIsNull(shareId, userId);
        return shareService.cancelShare(shareId, userId);
    }

    /***
     * 分享列表
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("分享列表")
    @GetMapping("/share/list")
    public ResponseResult<Object> sharelist(UploadApiParam upload) throws CommonException {
        return shareService.sharelist(upload);
    }

    /***
     * 访问分享链接
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("访问分享链接")
    @GetMapping("/public/access-share")
    @ResponseBody
    public ResponseResult<Object> accessShare(@RequestParam String share, Integer pageIndex, Integer pageSize) throws CommonException {
        ResultUtil.checkParamIsNull(share);
        return shareService.accessShare(share, pageIndex, pageSize);
    }

    /***
     * 访问分享链接里的目录
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("访问分享链接里的目录")
    @GetMapping("public/access-share/open")
    @ResponseBody
    public ResponseResult<Object> accessShareOpenDir(@RequestParam String share, @RequestParam String fileId, Integer pageIndex, Integer pageSize) throws CommonException {
        ShareBO shareBO = shareService.getShare(share);
        if(!shareService.checkWhetherExpired(shareBO)){
            return ResultUtil.warning("该分享以过期");
        }
        return shareService.accessShareOpenDir(shareBO, fileId, pageIndex, pageSize );
    }

    /**
     * 下载文件 转到 Nginx 下载
     * @param fileIds
     * @return
     */
    @ApiOperation("下载文件 转到 Nginx 下载")
    @GetMapping("/public/s/download")
    public void downLoad(HttpServletRequest request, HttpServletResponse response,@RequestParam String share, String[] fileIds) throws CommonException, IOException {
        boolean whetherExpired = shareService.checkWhetherExpired(share);
        if(whetherExpired){
            if (fileIds != null && fileIds.length > 0) {
                List<String> list = Arrays.asList(fileIds);
                fileService.publicNginx(request, response, list, true);
            } else {
                throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
            }
        }else{
            try (OutputStream out = response.getOutputStream()) {
                out.write("该分享以过期".getBytes());
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 预览文件
     * @param fileIds fileIds
     * @return
     */
    @ApiOperation("预览文件")
    @GetMapping("/public/s/preview/{filename}")
    public void preview(HttpServletRequest request, HttpServletResponse response, String[] fileIds,@PathVariable String filename) throws CommonException {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            fileService.publicNginx(request, response, list, false);
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
    @GetMapping("/public/s/view/thumbnail")
    public ResponseEntity<Object> thumbnail(HttpServletRequest request, String id) throws IOException {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id,null);
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getContent().length + "").header("Connection", "close")
                        .header(HttpHeaders.CONTENT_LENGTH, fileDocument.getContent().length + "")
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }
}
