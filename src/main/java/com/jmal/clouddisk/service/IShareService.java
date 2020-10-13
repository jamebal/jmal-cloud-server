package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
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
     * @param pageIndex
     * @param pageSize
     * @return
     */
    ResponseResult<Object> accessShare(String shareId, Integer pageIndex, Integer pageSize);

    /***
     * 获取分享信息
     * @param share
     * @return
     */
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

    /***
     * 打开目录
     * @param share
     * @param fileId
     * @param pageIndex
     * @param pageSize
     * @return
     */
    ResponseResult<Object> accessShareOpenDir(ShareBO share, String fileId, Integer pageIndex, Integer pageSize);

    /***
     * 获取分享列表
     * @param upload
     * @return
     */
    List<ShareBO> getShareList(UploadApiParamDTO upload);

    /***
     * 分享列表
     * @param upload
     * @return
     */
    ResponseResult<Object> shareList(UploadApiParamDTO upload);

    /***
     * 取消分享
     * @param shareIdList
     * @param userId
     * @return
     */
    ResponseResult<Object> cancelShare(List<String> shareIdList, String userId);
}
