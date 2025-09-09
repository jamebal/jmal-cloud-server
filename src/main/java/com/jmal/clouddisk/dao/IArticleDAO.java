package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.ArchivesVO;
import com.jmal.clouddisk.model.ArticleDTO;
import com.jmal.clouddisk.model.ArticleVO;
import com.jmal.clouddisk.model.file.FileDocument;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IArticleDAO {

    FileDocument getMarkdownOne(String mark);

    Page<FileDocument> getMarkdownList(ArticleDTO articleDTO);

    List<FileDocument> getAllReleaseArticles();

    Page<ArchivesVO> getArchives(Integer page, Integer pageSize);

    ArticleVO findBySlug(String slug);

    ArticleVO findById(String fileId);

    void updatePageSort(List<FileDocument> list);
}
