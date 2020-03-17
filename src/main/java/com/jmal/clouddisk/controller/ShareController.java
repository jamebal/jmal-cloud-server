package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Description share
 * @Author jmal
 * @Date 2020-03-17 16:22
 */
@RestController
public class ShareController {

    @Autowired
    IShareService shareService;

    /***
     * 生成分享链接
     * @param share
     * @return
     * @throws CommonException
     */
    @PostMapping("/share/generate")
    @ResponseBody
    public ResponseResult<Object> generateLink(@RequestBody ShareBO share) throws CommonException {
        ResultUtil.checkParamIsNull(share.getFileId(), share.getUserId());
        return shareService.generateLink(share);
    }

    /***
     * 访问分享链接
     * @param upload
     * @return
     * @throws CommonException
     */
    @GetMapping("/public/access-share")
    @ResponseBody
    public ResponseResult<Object> accessShare(@RequestParam String share) throws CommonException {
        return shareService.accessShare(share);
    }
}
