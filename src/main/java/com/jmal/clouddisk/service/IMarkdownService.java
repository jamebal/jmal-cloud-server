package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.util.ResponseResult;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author jmal
 * @Description 文档
 * @Date 2020/12/11 4:36 下午
 */
public interface IMarkdownService {
    /***
     * 获取markdown
     * @param articleDTO
     * @return
     */
    ResponseResult<FileDocument> getMarkDownOne(ArticleDTO articleDTO);

    /***
     * 获取markdown列表
     * @param articleDTO
     * @return
     */
    ResponseResult<List<MarkdownVO>> getMarkdownList(ArticleDTO articleDTO);

    /***
     * 获取文章列表
     * @param page page
     * @param pageSize pageSize
     * @return Page
     */
    Page<List<MarkdownVO>> getArticles(Integer page, Integer pageSize);

    /***
     * 获取独立页面列表
     * @return
     */
    List<MarkdownVO> getAlonePages();

    /***
     * 获取文章列表(根据分类)
     * @param page page
     * @param pageSize pageSize
     * @param categoryId categoryId
     * @return Page
     */
    Page<List<MarkdownVO>> getArticlesByCategoryId(Integer page, Integer pageSize, String categoryId);

    /***
     * 获取文章列表(根据标签)
     * @param page page
     * @param pageSize pageSize
     * @param tagId tagId
     * @return Page
     */
    Page<List<MarkdownVO>> getArticlesByTagId(int page, int pageSize, String tagId);

    /***
     * 获取文章列表(根据关键字)
     * @param page page
     * @param pageSize pageSize
     * @param keyword keyword
     * @return Page
     */
    Page<List<MarkdownVO>> getArticlesByKeyword(int page, int pageSize, String keyword);

    /***
     * 归档
     * @param page page
     * @param pageSize pageSize
     * @return Page
     */
    Page<Object> getArchives(Integer page, Integer pageSize);

    /***
     * 根据缩略名获取markdown内容
     * @param slug
     * @return
     */
    FileDocument getMarkDownContentBySlug(String slug);

    /***
     * 修改文档排序
     * @param fileId
     * @return
     */
    ResponseResult<Object> sortMarkdown(List<String> fileId);

    /***
     * 编辑文档
     * @param upload
     * @return
     */
    ResponseResult<Object> editMarkdown(ArticleParamDTO upload);

    /***
     * 删除草稿
     * @param fileId fileId
     * @param username username
     * @return ResponseResult
     */
    ResponseResult<Object> deleteDraft(String fileId, String username);

    /***
     * 编辑文档(根据path)
     * @param upload
     * @return
     */
    ResponseResult<Object> editMarkdownByPath(UploadApiParamDTO upload);

    /***
     * 上传文档里的图片
     * @param upload
     * @return
     */
    ResponseResult<Object> uploadMarkdownImage(UploadImageDTO upload);

    /***
     * 上传文档里链接图片
     * @param uploadImageDTO UploadImageDTO
     * @return
     */
    ResponseResult<Object> uploadMarkdownLinkImage(UploadImageDTO uploadImageDTO);

}
