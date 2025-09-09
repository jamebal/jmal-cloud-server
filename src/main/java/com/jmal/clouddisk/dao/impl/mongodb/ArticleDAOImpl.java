package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IArticleDAO;
import com.jmal.clouddisk.dao.impl.mongodb.repository.FileDocumentRepository;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.ArchivesVO;
import com.jmal.clouddisk.model.ArticleDTO;
import com.jmal.clouddisk.model.ArticleVO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.MyFileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class ArticleDAOImpl implements IArticleDAO {

    private final MongoTemplate mongoTemplate;

    private final FileDocumentRepository fileDocumentRepository;

    private final IUserService userService;

    private final CommonFileService commonFileService;

    private final FileProperties fileProperties;

    private final LuceneQueryService luceneQueryService;

    @Override
    public FileDocument getMarkdownOne(String mark) {
        FileDocument fileDocument = mongoTemplate.findById(mark, FileDocument.class, CommonFileService.COLLECTION_NAME);
        if (fileDocument != null) {
            String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
            fileDocument.setUsername(username);
            String currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath());
            File file = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileDocument.getName()).toFile();
            String content = FileUtil.readString(file, MyFileUtils.getFileCharset(file));
            fileDocument.setContentText(content);
        }
        return fileDocument;
    }

    @Override
    public Page<FileDocument> getMarkdownList(ArticleDTO articleDTO) {
        Query query = new Query();
        // 查询条件
        query.addCriteria(Criteria.where(Constants.SUFFIX).is("md"));
        query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(fileProperties.getDocumentDir())));
        if (!CharSequenceUtil.isBlank(articleDTO.getUserId())) {
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(articleDTO.getUserId()));
        }
        if (articleDTO.getIsRelease() != null && articleDTO.getIsRelease()) {
            query.addCriteria(Criteria.where(Constants.RELEASE).is(true));
        }
        if (articleDTO.getIsAlonePage() != null && articleDTO.getIsAlonePage()) {
            query.addCriteria(Criteria.where(Constants.ALONE_PAGE).exists(true));

            if (CharSequenceUtil.isBlank(articleDTO.getSortableProp()) || CharSequenceUtil.isBlank(articleDTO.getOrder())) {
                articleDTO.setOrder("ascending");
                articleDTO.setSortableProp("pageSort");
            }
        } else {
            query.addCriteria(Criteria.where(Constants.ALONE_PAGE).exists(false));
        }
        if (articleDTO.getIsDraft() != null && articleDTO.getIsDraft()) {
            query.addCriteria(Criteria.where(Constants.DRAFT).exists(true));
        }
        if (articleDTO.getCategoryIds() != null && articleDTO.getCategoryIds().length > 0) {
            query.addCriteria(Criteria.where("categoryIds").in((Object[]) articleDTO.getCategoryIds()));
        }
        if (articleDTO.getTagIds() != null && articleDTO.getTagIds().length > 0) {
            query.addCriteria(Criteria.where(Constants.TAG_IDS).in((Object[]) articleDTO.getTagIds()));
        }

        if (!CharSequenceUtil.isBlank(articleDTO.getKeyword())) {
            Set<String> luceneFileIds = luceneQueryService.findByArticleKeyword(articleDTO.getKeyword());
            query.addCriteria(Criteria.where("_id").in(luceneFileIds));
        }

        if (CharSequenceUtil.isBlank(articleDTO.getSortableProp()) || CharSequenceUtil.isBlank(articleDTO.getOrder())) {
            articleDTO.setOrder("descending");
            articleDTO.setSortableProp(Constants.UPLOAD_DATE);
        }

        // 只保留需要的字段
        query.fields()
                .include("_id")
                .include("cover")
                .include(Constants.FILENAME_FIELD)
                .include(IUserService.USER_ID)
                .include(Constants.CONTENT_TYPE)
                .include(Constants.UPDATE_DATE)
                .include(Constants.UPLOAD_DATE)
                .include(Constants.DRAFT)
                .include("pageSort")
                .include("slug")
                .include("suffix")
                .include(Constants.RELEASE)
                .include("tagIds")
                .include("categoryIds");

        long total = mongoTemplate.count(query, FileDocument.class);

        Pageable pageable = articleDTO.getPageable();
        if (total == 0) {
            return Page.empty(pageable);
        }
        query.with(pageable);


        List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, CommonFileService.COLLECTION_NAME);
        return  new PageImpl<>(fileDocumentList, pageable, total);
    }

    @Override
    public List<FileDocument> getAllReleaseArticles() {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.RELEASE).is(true));
        return mongoTemplate.find(query, FileDocument.class);
    }

    @Override
    public Page<ArchivesVO> getArchives(Integer page, Integer pageSize) {
        // 1. 获取总数
        long totalCount = fileDocumentRepository.countByReleaseIsTrueAndAlonePageExists(true, false);

        // 2. 准备分页参数
        boolean pagination = (page != null && pageSize != null);
        Pageable pageable = Pageable.unpaged();
        if (pagination) {
            pageable = PageRequest.of(page - 1, pageSize);
        }

        // 3. 执行聚合查询
        List<ArchivesVO> projections = fileDocumentRepository.findArchives(pageable);

        return new PageImpl<>(projections, pageable, totalCount);
    }

    @Override
    public ArticleVO findBySlug(String slug) {
        Query query = new Query();
        query.addCriteria(Criteria.where("slug").is(slug));
        return mongoTemplate.findOne(query, ArticleVO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public ArticleVO findById(String fileId) {
        return mongoTemplate.findById(fileId, ArticleVO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void updatePageSort(List<FileDocument> list) {
        list.parallelStream().forEach(doc -> {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(doc.getId()));
            Update update = new Update();
            update.set("pageSort", doc.getPageSort());
            mongoTemplate.updateFirst(query, update, CommonFileService.COLLECTION_NAME);
        });
    }

}
