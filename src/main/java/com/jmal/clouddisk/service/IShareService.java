package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.util.ResponseResult;

/**
 * @Description IShareService
 * @Author jmal
 * @Date 2020-03-17 16:21
 */
public interface IShareService {

    /***
     * 生成分享链接
     * @param share
     * @return
     */
    ResponseResult<Object> generateLink(ShareBO share);

    /***
     * 访问分享链接
     * @param shareId
     * @return
     */
    ResponseResult<Object> accessShare(String shareId);
}
