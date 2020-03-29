package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.util.ResponseResult;

import java.util.List;

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
    ResponseResult<Object> accessShare(String shareId, Integer pageIndex, Integer pageSize);

    ShareBO getShare(String share);

    /***
     * 检查是否过期
     * @param shareBO
     * @return
     */
    boolean checkWhetherExpired(ShareBO shareBO);

    /***
     * 检查是否过期
     * @param share
     * @return
     */
    boolean checkWhetherExpired(String share);

    ResponseResult<Object> accessShareOpenDir(ShareBO share, String fileId, Integer pageIndex, Integer pageSize);

    /***
     * sharelist
     * @param upload
     * @return
     */
    List<ShareBO> getShareList(UploadApiParam upload);

    /***
     * 分享列表
     * @param upload
     * @return
     */
    ResponseResult<Object> sharelist(UploadApiParam upload);

    /***
     * 取消分享
     * @param share
     * @return
     */
    ResponseResult<Object> cancelShare(String[] shareId, String userId);
}
