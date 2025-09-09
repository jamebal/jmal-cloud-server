package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.IArticleDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.article.ArticleOperation;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.ArchivesVO;
import com.jmal.clouddisk.model.ArticleDTO;
import com.jmal.clouddisk.model.ArticleVO;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.FilePersistenceService;
import com.jmal.clouddisk.util.JacksonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ArticleDAOJpaImpl implements IArticleDAO {

    private final DataSourceProperties dataSourceProperties;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final ArticleRepository articleRepository;

    private final FilePersistenceService filePersistenceService;

    private final LuceneQueryService luceneQueryService;

    private final IWriteService writeService;

    @Override
    public FileDocument getMarkdownOne(String fileId) {
        ArticleDO articleDO = articleRepository.findMarkdownByFileId(fileId).orElse(null);
        if (articleDO == null) {
            return null;
        }
        FileDocument fileDocument = articleDO.toFileDocument();
        if (BooleanUtil.isTrue(articleDO.getFileMetadata().getProps().getHasContentText())) {
            filePersistenceService.readContent(articleDO.getId(), "contentText").ifPresent(inputStream -> {
                try (inputStream) {
                    String contentText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    fileDocument.setContentText(contentText);
                } catch (Exception e) {
                    log.error("读取 ArticleDO contentText 失败, fileId: {}", fileId, e);
                }
            });
        }
        return fileDocument;
    }

    @Override
    public Page<FileDocument> getMarkdownList(ArticleDTO articleDTO) {

        // 全文检索查询符合关键字的文章ID
        Set<String> luceneFileIds = null;
        if (CharSequenceUtil.isNotBlank(articleDTO.getKeyword())) {
            luceneFileIds = luceneQueryService.findByArticleKeyword(articleDTO.getKeyword());
        }

        // 1. 构建动态SQL和参数
        Map<String, Object> params = new HashMap<>();
        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT DISTINCT a.file_id,a.category_ids,a.has_draft,a.slug,a.tag_ids,a.is_release,a.cover" +
                        ",f.content_type,f.name,f.update_date,f.upload_date,f.user_id,f.suffix " +
                        "FROM articles a "
        );

        StringBuilder whereBuild = new StringBuilder();

        if (articleDTO.getCategoryIds() != null && articleDTO.getCategoryIds().length > 0 && dataSourceProperties.getType() == DataSourceType.sqlite) {
            whereBuild.append(", json_each(a.category_ids) jec ");
        }

        if (articleDTO.getTagIds() != null && articleDTO.getTagIds().length > 0 && dataSourceProperties.getType() == DataSourceType.sqlite) {
            whereBuild.append(", json_each(a.tag_ids) jet ");
        }
        whereBuild.append("JOIN files f ON a.file_id = f.id " +
                " WHERE 1 = 1 ");

        // -- 动态添加WHERE子句 --
        if (CharSequenceUtil.isNotBlank(articleDTO.getUserId())) {
            whereBuild.append("AND f.user_id = :userId ");
            params.put(IUserService.USER_ID, articleDTO.getUserId());
        }
        if (articleDTO.getIsRelease() != null && articleDTO.getIsRelease()) {
            whereBuild.append("AND a.is_release = :release ");
            params.put(Constants.RELEASE, true);
        }

        if (articleDTO.getIsAlonePage() != null && articleDTO.getIsAlonePage()) {
            whereBuild.append("AND a.alone_page IS NOT NULL ");
        } else {
            whereBuild.append("AND a.alone_page IS NULL ");
        }

        if (articleDTO.getIsDraft() != null && articleDTO.getIsDraft()) {
            whereBuild.append("AND a.has_draft = true ");
        }

        if (articleDTO.getCategoryIds() != null && articleDTO.getCategoryIds().length > 0) {
            if (dataSourceProperties.getType() == DataSourceType.sqlite) {
                whereBuild.append("AND jec.value IN (:categoryIds) ");
                params.put("categoryIds", List.of(articleDTO.getCategoryIds()));
            } else if (dataSourceProperties.getType() == DataSourceType.pgsql) {
                whereBuild.append("AND category_ids ?| :categoryIds ");
                params.put("categoryIds", List.of(articleDTO.getCategoryIds()));
            } else if (dataSourceProperties.getType() == DataSourceType.mysql) {
                whereBuild.append("AND JSON_OVERLAPS(category_ids, :categoryIdListAsJson) ");
                String categoryIdListAsJson = JacksonUtil.toJSONString(articleDTO.getCategoryIds());
                params.put("categoryIdListAsJson", categoryIdListAsJson);
            }
        }

        if (articleDTO.getTagIds() != null && articleDTO.getTagIds().length > 0) {
            if (dataSourceProperties.getType() == DataSourceType.sqlite) {
                whereBuild.append("AND jet.value IN (:tagIds) ");
                params.put("tagIds", List.of(articleDTO.getTagIds()));
            } else if (dataSourceProperties.getType() == DataSourceType.pgsql) {
                whereBuild.append("AND tag_ids ?| :tagIds ");
                params.put("tagIds", List.of(articleDTO.getTagIds()));
            } else if (dataSourceProperties.getType() == DataSourceType.mysql) {
                whereBuild.append("AND JSON_OVERLAPS(tag_ids, :tagIdListAsJson) ");
                String tagIdListAsJson = JacksonUtil.toJSONString(articleDTO.getTagIds());
                params.put("tagIdListAsJson", tagIdListAsJson);
            }
        }

        if (luceneFileIds != null) {
            // 如果luceneFileIds不为null，说明进行了关键字搜索
            whereBuild.append("AND a.file_id IN (:luceneFileIds) ");
            params.put("luceneFileIds", luceneFileIds);
        }

        // 2. 构建Count查询
        String countSql = "SELECT count(DISTINCT a.file_id) FROM articles a " + whereBuild;
        Long total = jdbcTemplate.queryForObject(countSql, params, Long.class);

        Pageable pageable = articleDTO.getPageable();
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        sqlBuilder.append(whereBuild);

        // 3. 添加排序和分页
        // 排序逻辑
        StringBuilder orderByClause = new StringBuilder("ORDER BY ");
        if (articleDTO.getIsAlonePage() != null && articleDTO.getIsAlonePage()) {
            orderByClause.append("a.page_sort ASC ");
        } else {
            orderByClause.append("f.upload_date DESC ");
        }
        sqlBuilder.append(orderByClause);
        // 分页逻辑
        if (pageable.isPaged()) {
            sqlBuilder.append("LIMIT :limit OFFSET :offset ");
            params.put("limit", pageable.getPageSize());
            params.put("offset", pageable.getOffset());
        }

        // 4. 执行主查询
        List<FileDocument> articles = jdbcTemplate.query(sqlBuilder.toString(), params, (rs, _) -> {
            FileDocument fileDocument = new FileDocument();
            fileDocument.setId(rs.getString("file_id"));
            fileDocument.setCover(rs.getString("cover"));
            fileDocument.setUserId(rs.getString("user_id"));
            fileDocument.setName(rs.getString("name"));
            fileDocument.setContentType(rs.getString("content_type"));
            fileDocument.setUploadDate(rs.getTimestamp("upload_date").toLocalDateTime());
            fileDocument.setUpdateDate(rs.getTimestamp("update_date").toLocalDateTime());
            fileDocument.setSlug(rs.getString("slug"));
            fileDocument.setSuffix(rs.getString("suffix"));
            fileDocument.setRelease(rs.getBoolean("is_release"));
            fileDocument.setHasDraft(rs.getBoolean("has_draft"));
            String categoryIdsJson = rs.getString("category_ids");
            if (CharSequenceUtil.isNotBlank(categoryIdsJson)) {
                String[] categoryIds = JacksonUtil.parseArray(categoryIdsJson, String.class).toArray(new String[0]);
                fileDocument.setCategoryIds(categoryIds);
            }
            return fileDocument;
        });

        articles.parallelStream().forEach(doc -> {
            if (BooleanUtil.isTrue(doc.isHasDraft())) {
                filePersistenceService.readContent(doc.getId(), "draft").ifPresent(inputStream -> {
                    try (inputStream) {
                        String contentText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                        doc.setDraft(contentText);
                    } catch (Exception e) {
                        log.error("读取 ArticleDO draft 失败, fileId: {}", doc.getId(), e);
                    }
                });
            }
        });

        return new PageImpl<>(articles, articleDTO.getPageable(), total);
    }

    @Override
    public List<FileDocument> getAllReleaseArticles() {
        List<ArticleDO> articleDOList = articleRepository.findByReleaseIsTrue();
        return articleDOList.stream().map(ArticleDO::toFileDocument).toList();
    }

    @Override
    public Page<ArchivesVO> getArchives(Integer page, Integer pageSize) {
        boolean pagination = (page != null && pageSize != null);
        Pageable pageable = Pageable.unpaged();
        if (pagination) {
            pageable = PageRequest.of(page - 1, pageSize);
        }
        return articleRepository.findArchives(pageable);
    }

    @Override
    public ArticleVO findBySlug(String slug) {
        ArticleDO articleDO = articleRepository.findBySlug(slug).orElse(null);
        if (articleDO == null) {
            return null;
        }
        ArticleVO articleVO = articleDO.toArticleVO();
        articleVOFilePersistence(articleDO, articleVO);
        return  articleVO;
    }

    @Override
    public ArticleVO findById(String fileId) {
        ArticleDO articleDO = articleRepository.findMarkdownByFileId(fileId).orElse(null);
        if (articleDO == null) {
            return null;
        }
        ArticleVO articleVO = articleDO.toArticleVO();
        articleVOFilePersistence(articleDO, articleVO);
        return  articleVO;
    }

    @Override
    public void updatePageSort(List<FileDocument> sortList) {
        sortList.forEach(fileDocument -> writeService.submit(new ArticleOperation.UpdatePageSortById(fileDocument.getId(), fileDocument.getPageSort())));
    }

    private void articleVOFilePersistence(ArticleDO articleDO, ArticleVO articleVO) {
        if (BooleanUtil.isTrue(articleDO.getFileMetadata().getProps().getHasHtml())) {
            filePersistenceService.readContent(articleDO.getFileMetadata().id, "html").ifPresent(inputStream -> {
                try (inputStream) {
                    String contentText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    articleVO.setHtml(contentText);
                } catch (Exception e) {
                    log.error("读取 html 失败, fileId: {}", articleDO.getFileMetadata().id, e);
                }
            });
        }
        if (BooleanUtil.isTrue(articleDO.getFileMetadata().getProps().getHasContentText())) {
            filePersistenceService.readContent(articleDO.getFileMetadata().id, "contentText").ifPresent(inputStream -> {
                try (inputStream) {
                    String contentText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    articleVO.setContentText(contentText);
                } catch (Exception e) {
                    log.error("读取 contentText 失败, fileId: {}", articleDO.getFileMetadata().id, e);
                }
            });
        }
    }

}
