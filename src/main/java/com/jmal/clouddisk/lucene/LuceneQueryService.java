package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jmal.clouddisk.lucene.LuceneService.FIELD_TAG_NAME_FUZZY;

/**
 * Lucene 精确查询
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LuceneQueryService {

    private final SearcherManager searcherManager;
    private final Analyzer analyzer;
    private final FileProperties fileProperties;

    public boolean existsSha256(String fileIndexHash) {
        IndexSearcher indexSearcher = null;
        try {
            searcherManager.maybeRefresh();
            indexSearcher = searcherManager.acquire();
            Term term = new Term(Constants.ETAG, fileIndexHash);
            Query query = new TermQuery(term);
            TopDocs topDocs = indexSearcher.search(query, 1);
            return topDocs.totalHits.value() > 0;
        } catch (IOException e) {
            log.error("检查 {} 是否存在失败", Constants.ETAG, e);
        } finally {
            if (indexSearcher != null) {
                try {
                    searcherManager.release(indexSearcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
        }
        return false;
    }

    public long countByTagId(String tagId) {
        return count(getTagIdQuery(tagId));
    }

    public Set<String> findByTagId(String tagId) {
        return find(getTagIdQuery(tagId));
    }

    public Page<String> findByUserIdAndType(String userId, String type, SearchDTO searchDTO) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(getUserIdQuery(userId), BooleanClause.Occur.MUST);
        builder.add(getTypeQuery(type), BooleanClause.Occur.MUST);
        return find(builder.build(), searchDTO);
    }

    public Page<String> findByUserIdAndTagId(String userId, String tagId, SearchDTO searchDTO) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(getUserIdQuery(userId), BooleanClause.Occur.MUST);
        builder.add(getTagIdQuery(tagId), BooleanClause.Occur.MUST);
        return find(builder.build(), searchDTO);
    }

    public Page<String> findByUserIdAndPath(String userId, String path, SearchDTO searchDTO) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(getUserIdQuery(userId), BooleanClause.Occur.MUST);
        builder.add(getPathQuery(path), BooleanClause.Occur.MUST);
        return find(builder.build(), searchDTO);
    }

    public Page<String> findByUserIdAndIsFolder(String userId, Boolean isFolder, SearchDTO searchDTO) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(getUserIdQuery(userId), BooleanClause.Occur.MUST);
        builder.add(getIsFolderQuery(isFolder), BooleanClause.Occur.MUST);
        return find(builder.build(), searchDTO);
    }

    public Page<String> findByUserIdAndIsFavorite(String userId, Boolean isFavorite, SearchDTO searchDTO) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(getUserIdQuery(userId), BooleanClause.Occur.MUST);
        builder.add(getIsFavoriteQuery(isFavorite), BooleanClause.Occur.MUST);
        return find(builder.build(), searchDTO);
    }

    public Query getIsFolderQuery(Boolean isFolder) {
        return IntPoint.newExactQuery(Constants.IS_FOLDER, isFolder ? 1 : 0);
    }

    public Query getIsFavoriteQuery(Boolean isFavorite) {
        return IntPoint.newExactQuery(Constants.IS_FAVORITE, isFavorite ? 1 : 0);
    }

    public Query getTagIdQuery(String tagId) {
        return new TermQuery(new Term(LuceneService.FIELD_TAG_ID, tagId));
    }

    public Query getPathQuery(String path) {
        return new TermQuery(new Term(Constants.PATH_FIELD, path));
    }

    public Query getTypeQuery(String type) {
        return new TermQuery(new Term("type", type));
    }

    public Query getUserIdQuery(String userId) {
        return new TermQuery(new Term(IUserService.USER_ID, userId));
    }

    public long count(Query query) {
        IndexSearcher indexSearcher = null;
        try {
            searcherManager.maybeRefresh();
            indexSearcher = searcherManager.acquire();
            TopDocs topDocs = indexSearcher.search(query, Integer.MAX_VALUE);
            return topDocs.totalHits.value();
        } catch (IOException e) {
            log.error("查询失败, query: {}", query.toString(), e);
        } finally {
            if (indexSearcher != null) {
                try {
                    searcherManager.release(indexSearcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
        }
        return 0;
    }

    public Set<String> findByArticleKeyword(String keyword) {
        SearchDTO.SearchDTOBuilder builder = SearchDTO.builder();
        builder.keyword(keyword)
                .exactSearch(fileProperties.getExactSearch())
                .includeFileName(true)
                .includeFileContent(true)
                .build();
        try {

            BooleanQuery.Builder fileQueryBuilder = new BooleanQuery.Builder();
            BooleanQuery keywordQuery = getKeywordQuery(builder.build());
            fileQueryBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
            fileQueryBuilder.add(LuceneQueryService.getPathQueryBuilder(fileProperties.getDocumentDir()), BooleanClause.Occur.MUST);
            return find(keywordQuery);
        } catch (ParseException e) {
            throw new CommonException(e.getMessage());
        }
    }

    public Page<String> find(Query query, SearchDTO searchDTO) {
        IndexSearcher indexSearcher = null;
        try {
            searcherManager.maybeRefresh();
            indexSearcher = searcherManager.acquire();
            ScoreDoc lastScoreDoc = null;
            int pageNum = searchDTO.getPage();
            int pageSize = searchDTO.getPageSize();
            Sort sort = getSort(searchDTO);
            if (pageNum > 1) {
                int totalHitsToSkip = (pageNum - 1) * pageSize;
                TopDocs topDocs = indexSearcher.search(query, totalHitsToSkip, sort);
                if (topDocs.scoreDocs.length == totalHitsToSkip) {
                    lastScoreDoc = topDocs.scoreDocs[totalHitsToSkip - 1];
                }
            }
            int count = indexSearcher.count(query);
            TopDocs topDocs = indexSearcher.searchAfter(lastScoreDoc, query, pageSize, sort);
            Set<String> fileIds = getFileIds(indexSearcher, topDocs);
            return new PageImpl<>(List.copyOf(fileIds), Pageable.ofSize(pageSize).withPage(pageNum - 1), count);
        } catch (IOException e) {
            log.error("查询失败, query: {}", query.toString(), e);
        } finally {
            if (indexSearcher != null) {
                try {
                    searcherManager.release(indexSearcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
        }
        return Page.empty();
    }

    public Set<String> find(Query query) {
        IndexSearcher indexSearcher = null;
        try {
            searcherManager.maybeRefresh();
            indexSearcher = searcherManager.acquire();
            TopDocs topDocs = indexSearcher.search(query, Integer.MAX_VALUE);
            return getFileIds(indexSearcher, topDocs);
        } catch (IOException e) {
            log.error("查询失败, query: {}", query.toString(), e);
        } finally {
            if (indexSearcher != null) {
                try {
                    searcherManager.release(indexSearcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
        }
        return Set.of();
    }

    private Set<String> getFileIds(IndexSearcher indexSearcher, TopDocs topDocs) throws IOException {
        Set<String> fileIds = new HashSet<>();
        StoredFields storedFields = indexSearcher.storedFields();
        for (ScoreDoc hit : topDocs.scoreDocs) {
            Document doc = storedFields.document(hit.doc);
            String id = doc.get("id");
            if (id != null) {
                fileIds.add(id);
            }
        }
        return fileIds;
    }

    /**
     * 关键字查询
     * @param searchDTO searchDTO
     */
    public BooleanQuery getKeywordQuery(SearchDTO searchDTO) throws ParseException {
        // 精确搜索时，通常不希望对用户输入做过多改动
        String exactKeyword = searchDTO.getKeyword().toLowerCase();

        // 模糊搜索将关键字中的特殊字符转义
        String fuzzyKeyword = QueryParser.escape(searchDTO.getKeyword().toLowerCase().trim());

        // filename 和 tag Name  进行 NGram 匹配
        BooleanQuery.Builder nameAndTagQueryBuilder = new BooleanQuery.Builder();
        if (BooleanUtil.isTrue(searchDTO.getIncludeFileName())) {
            BooleanQuery.Builder filenameBooleanQueryBuilder = getBooleanQueryFieldBuilder(LuceneService.FIELD_FILENAME_NGRAM, LuceneService.FIELD_FILENAME_FUZZY, exactKeyword, fuzzyKeyword, searchDTO);
            nameAndTagQueryBuilder.add(filenameBooleanQueryBuilder.build(), BooleanClause.Occur.SHOULD);
        }
        if (BooleanUtil.isTrue(searchDTO.getIncludeTagName())) {
            BooleanQuery.Builder tagNameBooleanQueryBuilder = getBooleanQueryFieldBuilder(LuceneService.FIELD_TAG_NAME_NGRAM, FIELD_TAG_NAME_FUZZY, exactKeyword, fuzzyKeyword, searchDTO);
            nameAndTagQueryBuilder.add(tagNameBooleanQueryBuilder.build(), BooleanClause.Occur.SHOULD);
        }

        Query boostQuery = new BoostQuery(nameAndTagQueryBuilder.build(), 3.0f);

        Query contentQuery = null;
        if (BooleanUtil.isTrue(searchDTO.getIncludeFileContent())) {
            BooleanQuery.Builder contentBooleanQueryBuilder = getBooleanQueryFieldBuilder(LuceneService.FIELD_CONTENT_NGRAM, LuceneService.FIELD_CONTENT_FUZZY, exactKeyword, fuzzyKeyword, searchDTO);
            contentQuery = contentBooleanQueryBuilder.build();
        }

        // 如果keyword中有空格，将其拆分为多个关键字，这多个关键字之间是OR关系,在name或tag字段
        Query nameAndTagQuery = null;
        if (!BooleanUtil.isTrue(searchDTO.getExactSearch())) {
            nameAndTagQuery = getMultipleKeywordsQuery(searchDTO, exactKeyword);
        }

        // 将正则表达式查询、短语查询和分词匹配查询组合成一个查询（OR关系）
        BooleanQuery.Builder combinedQueryBuilder = new BooleanQuery.Builder().add(boostQuery, BooleanClause.Occur.SHOULD);
        if (contentQuery != null) {
            combinedQueryBuilder.add(contentQuery, BooleanClause.Occur.SHOULD);
        }

        if (nameAndTagQuery != null) {
            combinedQueryBuilder.add(nameAndTagQuery, BooleanClause.Occur.SHOULD);
        }
        // 关键查询
        return combinedQueryBuilder.build();
    }

    /**
     * 创建字段查询
     * 如果开启了精准搜索配置，且根据参数exactSearch为true，则创建精确查询；否则创建模糊查询
     * 如果禁用精准搜索配置，则为“content”以外的字段(“filename”和“tagName”)创建模糊查询和精准查询, 两种查询使用OR组合
     * 由于精确搜索配置导致前端没有exactSearch参数，默认使用NGram分词器索引“filename”和“tagName”或模糊查询组合查询将会有更好的效果”对于未来的维护者来说将非常有益
     *
     * @param fieldNameExact 精准查询字段名
     * @param fieldNameFuzzy 模糊查询字段名
     * @param exactKeyword   精准查询关键字
     * @param fuzzyKeyword   模糊查询关键字
     * @param searchDTO      查询参数
     */
    private BooleanQuery.Builder getBooleanQueryFieldBuilder(String fieldNameExact, String fieldNameFuzzy, String exactKeyword, String fuzzyKeyword, SearchDTO searchDTO) throws ParseException {
        BooleanQuery.Builder booleanQueryFieldBuilder = new BooleanQuery.Builder();
        Query fuzzyQuery = null;
        Query exactQuery = null;
        if (fileProperties.getExactSearch()) {
            exactQuery = getExactQuery(fieldNameExact, exactKeyword);
            if (!BooleanUtil.isTrue(searchDTO.getExactSearch())) {
                fuzzyQuery = getFuzzyQuery(fieldNameFuzzy, fuzzyKeyword);
            }
        } else {
            if (LuceneService.FIELD_CONTENT_FUZZY.equals(fieldNameFuzzy)) {
                fuzzyQuery = getFuzzyQuery(fieldNameFuzzy, fuzzyKeyword);
            } else {
                fuzzyQuery = getFuzzyQuery(fieldNameFuzzy, fuzzyKeyword);
                exactQuery = getExactQuery(fieldNameExact, exactKeyword);
            }
        }
        if (fuzzyQuery != null) {
            booleanQueryFieldBuilder.add(fuzzyQuery, BooleanClause.Occur.SHOULD);
        }
        if (exactQuery != null) {
            booleanQueryFieldBuilder.add(exactQuery, BooleanClause.Occur.SHOULD);
        }
        return booleanQueryFieldBuilder;
    }

    private Query getMultipleKeywordsQuery(SearchDTO searchDTO, String exactKeyword) {
        if (exactKeyword.contains(" ") && (BooleanUtil.isTrue(searchDTO.getIncludeFileName()) || BooleanUtil.isTrue(searchDTO.getIncludeTagName()))) {
            BooleanQuery.Builder nameAndTagMultipleWordsQueryBuilder = new BooleanQuery.Builder();
            for (String key : exactKeyword.split(" ")) {

                if (BooleanUtil.isTrue(searchDTO.getIncludeFileName())) {
                    Query filenameQuery = getExactQuery(LuceneService.FIELD_FILENAME_NGRAM, key);
                    nameAndTagMultipleWordsQueryBuilder.add(filenameQuery, BooleanClause.Occur.SHOULD);
                }
                if (BooleanUtil.isTrue(searchDTO.getIncludeTagName())) {
                    Query tagNameQuery = getExactQuery(LuceneService.FIELD_TAG_NAME_NGRAM, key);
                    nameAndTagMultipleWordsQueryBuilder.add(tagNameQuery, BooleanClause.Occur.SHOULD);
                }

            }
            return nameAndTagMultipleWordsQueryBuilder.build();
        }
        return null;
    }

    private Query getFuzzyQuery(String fieldNameFuzzy, String fuzzyKeyword) throws ParseException {
        Query contentQuery;
        QueryParser contentParser = new QueryParser(fieldNameFuzzy, analyzer);
        contentParser.setDefaultOperator(QueryParser.Operator.AND);
        contentQuery = contentParser.parse(QueryParser.escape(fuzzyKeyword.trim()));
        return contentQuery;
    }

    private Query getExactQuery(String fieldName, String exactSearchTerm) {
        Query contentQuery;
        if (CharSequenceUtil.isBlank(exactSearchTerm)) {
            contentQuery = new MatchNoDocsQuery();
        } else if (exactSearchTerm.length() <= fileProperties.getNgramMaxSize()) {
            Term term = new Term(fieldName, exactSearchTerm);
            contentQuery = new TermQuery(term);
        } else {
            // 如果用户输入长度大于 maxGramSize，则分解查询字符串
            BooleanQuery.Builder decomposedQueryBuilder = new BooleanQuery.Builder();
            boolean hasValidSubTerms = false;

            // 生成重叠的、长度为 maxGram 的子串
            // 滑动窗口：从索引 0 开始，每次取长度为 maxGram 的子串，然后窗口向右移动一个字符
            for (int i = 0; i <= exactSearchTerm.length() - fileProperties.getNgramMaxSize(); i++) {
                String subTerm = exactSearchTerm.substring(i, i + fileProperties.getNgramMaxSize());
                if (CharSequenceUtil.isNotBlank(subTerm)) { // 理论上 subTerm 不会为空，但作为防御
                    decomposedQueryBuilder.add(new TermQuery(new Term(fieldName, subTerm)), BooleanClause.Occur.MUST);
                    hasValidSubTerms = true;
                }
            }
            if (hasValidSubTerms) {
                contentQuery = decomposedQueryBuilder.build();
                log.debug("{} 精确子串搜索 (长查询分解后): {}", fieldName, contentQuery.toString());
            } else {
                // 如果分解后没有有效的子查询（例如，原始字符串非常特殊或maxGram设置不当导致无法分解）
                // 虽然理论上 exactSearchTerm.length() > maxGram 应该总能分解出至少一个，但作为防御
                contentQuery = new MatchNoDocsQuery();
            }
        }
        return contentQuery;
    }

    public static BooleanQuery getPathQueryBuilder(String path) {
        // 检查path最后一个字符是否有/, 如果没有，则添加
        path = path.endsWith("/") ? path : path + "/";
        BooleanQuery.Builder pathQueryBuilder = new BooleanQuery.Builder();
        Term pathTerm = new Term("path", path);
        PrefixQuery prefixQuery = new PrefixQuery(pathTerm);
        pathQueryBuilder.add(new TermQuery(pathTerm), BooleanClause.Occur.SHOULD);
        pathQueryBuilder.add(prefixQuery, BooleanClause.Occur.SHOULD);
        return pathQueryBuilder.build();
    }

    /**
     * 获取排序规则
     *
     * @param searchDTO searchDTO
     * @return Sort
     */
    private static Sort getSort(SearchDTO searchDTO) {
        // 如果 searchDTO 为 null，返回默认相关度倒序排序
        if (searchDTO == null) {
            return Sort.RELEVANCE;
        }

        String sortProp = searchDTO.getSortProp();
        String sortOrder = searchDTO.getSortOrder();

        // 如果排序属性或顺序为空，默认按相关度倒序排序
        if (CharSequenceUtil.isBlank(sortProp) || CharSequenceUtil.isBlank(sortOrder)) {
            return Sort.RELEVANCE;
        }

        // 确定排序方向
        boolean reverse = Constants.DESCENDING.equalsIgnoreCase(sortOrder);

        // 根据排序属性创建对应的 SortField
        SortField sortField;
        switch (sortProp.toLowerCase()) {
            case "isfolder":
                sortField = new SortField(Constants.IS_FOLDER, SortField.Type.INT, reverse);
                break;
            case "updatedate":
                sortField = new SortField("modified", SortField.Type.LONG, reverse);
                break;
            case Constants.SIZE:
                sortField = new SortField(Constants.SIZE, SortField.Type.LONG, reverse);
                break;
            case "name":
                return reverse ? Sort.RELEVANCE : new Sort(new SortField(null, SortField.Type.SCORE, true));
            // case "name":
            //     sortField = new SortField(LuceneService.FIELD_FILENAME_FUZZY, SortField.Type.STRING, reverse);
            default:
                // 对于不支持的排序字段，默认按相关度倒序
                return Sort.RELEVANCE;
        }

        return new Sort(sortField);
    }
}
