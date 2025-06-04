package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.TagService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import com.mongodb.client.AggregateIterable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

import static com.jmal.clouddisk.service.impl.CommonFileService.COLLECTION_NAME;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchFileService {

    private final Analyzer analyzer;
    private final FileProperties fileProperties;
    private final SearcherManager searcherManager;
    private final UserLoginHolder userLoginHolder;
    private final TagService tagService;
    private final MongoTemplate mongoTemplate;
    private final IUserService userService;

    public ResponseResult<List<FileIntroVO>> searchFile(SearchDTO searchDTO) {
        String keyword = searchDTO.getKeyword();
        if (keyword == null || keyword.trim().isEmpty() || searchDTO.getUserId() == null) {
            return ResultUtil.success(Collections.emptyList());
        }
        ResponseResult<List<FileIntroVO>> result = ResultUtil.genResult();
        try {
            beforeQuery(searchDTO);
            if (!searchDTO.getUserId().equals(userLoginHolder.getUserId())) {
                Map<String, Object> props = new HashMap<>();
                props.put("fileUsername", userService.getUserNameById(searchDTO.getUserId()));
                result.setProps(props);
            }

            int pageNum = searchDTO.getPage();
            int pageSize = searchDTO.getPageSize();
            searcherManager.maybeRefresh();
            List<String> seenIds = new ArrayList<>();
            IndexSearcher indexSearcher = searcherManager.acquire();
            Query query = getQuery(searchDTO);
            Sort sort = getSort(searchDTO);
            log.info("搜索关键字: {}", query.toString());
            log.info("排序规则: {}", sort);
            ScoreDoc lastScoreDoc = null;
            if (pageNum > 1) {
                int totalHitsToSkip = (pageNum - 1) * pageSize;
                TopDocs topDocs = indexSearcher.search(query, totalHitsToSkip, sort);
                if (topDocs.scoreDocs.length == totalHitsToSkip) {
                    lastScoreDoc = topDocs.scoreDocs[totalHitsToSkip - 1];
                }
            }
            int count = indexSearcher.count(query);
            TopDocs topDocs = indexSearcher.searchAfter(lastScoreDoc, query, pageSize, sort);
            for (ScoreDoc hit : topDocs.scoreDocs) {
                indexSearcher.storedFields().document(hit.doc);
                Document doc = indexSearcher.storedFields().document(hit.doc);
                String id = doc.get("id");
                seenIds.add(id);
            }
            List<FileIntroVO> fileIntroVOList = getFileIntroVOs(seenIds);
            long now = System.currentTimeMillis();
            fileIntroVOList.forEach(fileIntroVO -> {
                long updateMilli = TimeUntils.getMilli(fileIntroVO.getUpdateDate());
                fileIntroVO.setAgoTime(now - updateMilli);
            });
            result.setData(fileIntroVOList);
            result.setCount(count);

            String userId = userLoginHolder.getUserId();
            // 添加搜索历史
            Completable.fromAction(() -> addSearchHistory(userId, searchDTO)).subscribeOn(Schedulers.io()).subscribe();

            return result;
        } catch (IOException | ParseException | java.lang.IllegalArgumentException e) {
            log.error("搜索失败", e);
            return result.setData(Collections.emptyList()).setCount(0);
        }
    }

    /**
     * 添加搜索历史
     * @param searchUserId searchUserId
     * @param searchDTO searchDTO
     */
    private void addSearchHistory(String searchUserId, SearchDTO searchDTO) {
        if (CharSequenceUtil.isBlank(searchUserId) || searchDTO == null) {
            return;
        }
        SearchOptionHistoryDO searchOptionHistoryDO = searchDTO.toSearchOptionDO();
        searchOptionHistoryDO.setSearchTime(System.currentTimeMillis());
        searchOptionHistoryDO.setSearchUserId(searchUserId);
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("keyword").is(searchDTO.getKeyword()));
        query.addCriteria(Criteria.where("searchUserId").is(searchUserId));
        // 删除重复的搜索记录
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);

        // 删除一个月之前的搜索记录
        org.springframework.data.mongodb.core.query.Query query1 = new org.springframework.data.mongodb.core.query.Query();
        query1.addCriteria(Criteria.where("searchUserId").is(searchUserId));
        query1.addCriteria(Criteria.where("searchTime").lt(System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L));
        mongoTemplate.remove(query1, SearchOptionHistoryDO.class);

        // 插入搜索记录
        mongoTemplate.insert(searchOptionHistoryDO);
    }

    public List<SearchDTO> recentlySearchHistory(String keyword) {
        // 最近8条搜索记录
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("searchUserId").is(userLoginHolder.getUserId()));
        if (CharSequenceUtil.isNotBlank(keyword)) {
            query.addCriteria(Criteria.where("keyword").regex(keyword));
        }
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "searchTime"));
        query.limit(6);
        List<SearchOptionHistoryDO> searchOptionHistoryDOList = mongoTemplate.find(query, SearchOptionHistoryDO.class);
        return searchOptionHistoryDOList.stream().map(SearchOptionHistoryDO::toSearchDTO).toList();
    }

    public void deleteSearchHistory(String id) {
        if (CharSequenceUtil.isBlank(id)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);
    }

    public void deleteAllSearchHistory(String userId) {
        if (CharSequenceUtil.isBlank(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);
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
        boolean reverse = "descending".equalsIgnoreCase(sortOrder);

        // 根据排序属性创建对应的 SortField
        SortField sortField;
        switch (sortProp.toLowerCase()) {
            case "updatedate":
                sortField = new SortField("modified", SortField.Type.LONG, reverse);
                break;
            case "size":
                sortField = new SortField("size", SortField.Type.LONG, reverse);
                break;
            case "name":  // 显式支持相关度排序
                // 注意：FIELD_SCORE 本身是降序的，如果要求升序需要特殊处理
                return reverse ? Sort.RELEVANCE : new Sort(new SortField(null, SortField.Type.SCORE, true));
            default:
                // 对于不支持的排序字段，默认按相关度倒序
                return Sort.RELEVANCE;
        }

        return new Sort(sortField);
    }

    /**
     * 构建查询器
     *
     * @param searchDTO searchDTO
     * @return Query
     * @throws ParseException ParseException
     */
    private Query getQuery(SearchDTO searchDTO) throws ParseException {
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
            BooleanQuery.Builder tagNameBooleanQueryBuilder = getBooleanQueryFieldBuilder(LuceneService.FIELD_TAG_NAME_NGRAM, LuceneService.FIELD_TAG_NAME_FUZZY, exactKeyword, fuzzyKeyword, searchDTO);
            nameAndTagQueryBuilder.add(tagNameBooleanQueryBuilder.build(), BooleanClause.Occur.SHOULD);
        }

        Query boostQuery = new BoostQuery(nameAndTagQueryBuilder.build(), 3.0f);

        Query contentQuery = null;
        if (BooleanUtil.isTrue(searchDTO.getIncludeFileContent())) {
            BooleanQuery.Builder contentBooleanQueryBuilder = getBooleanQueryFieldBuilder(LuceneService.FIELD_CONTENT_NGRAM, LuceneService.FIELD_CONTENT_FUZZY, exactKeyword, fuzzyKeyword, searchDTO);
            contentQuery = contentBooleanQueryBuilder.build();
        }

        // 如果keyword中有空格，将其拆分为多个关键字，这多个关键字之间是AND关系,在name或tag字段
        Query nameAndTagQuery = null;
        if (!BooleanUtil.isTrue(searchDTO.getExactSearch())) {
            nameAndTagQuery = getMultipleKeywordsQuery(searchDTO, fuzzyKeyword);
        }

        // 将正则表达式查询、短语查询和分词匹配查询组合成一个查询（OR关系）
        BooleanQuery.Builder combinedQueryBuilder = new BooleanQuery.Builder().add(boostQuery, BooleanClause.Occur.SHOULD);
        if (contentQuery != null) {
            combinedQueryBuilder.add(contentQuery, BooleanClause.Occur.SHOULD);
        }

        if (nameAndTagQuery != null) {
            combinedQueryBuilder.add(nameAndTagQuery, BooleanClause.Occur.SHOULD);
        }

        // 其他通用搜索选项
        BooleanQuery otherQuery = getOtherOption(searchDTO);

        BooleanQuery combinedQuery = combinedQueryBuilder.build();

        // 创建最终查询（AND关系）
        BooleanQuery.Builder fileQueryBuilder = new BooleanQuery.Builder().add(new TermQuery(new Term(IUserService.USER_ID, searchDTO.getSearchUserId())), BooleanClause.Occur.MUST);

        if (!combinedQuery.clauses().isEmpty()) {
            fileQueryBuilder.add(combinedQuery, BooleanClause.Occur.MUST);
        }

        if (!otherQuery.clauses().isEmpty()) {
            fileQueryBuilder.add(otherQuery, BooleanClause.Occur.MUST);
        }

        // 添加其他不通用查询条件
        otherQueryParams(searchDTO, fileQueryBuilder);

        // or 挂载点查询
        Query mountQuery = getMountQueryBuilder(searchDTO, combinedQuery, otherQuery);

        // 构建最终查询
        BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder();

        // 是否仅搜索挂载文件
        if (!searchDTO.onlySearchMount()) {
            finalQueryBuilder.add(fileQueryBuilder.build(), BooleanClause.Occur.SHOULD);
        }

        // 是否搜索挂载文件
        if (mountQuery != null) {
            finalQueryBuilder.add(mountQuery, BooleanClause.Occur.SHOULD);
        }

        return finalQueryBuilder.build();
    }

    /**
     * 创建字段查询
     * 如果开启了精准搜索配置，且根据参数exactSearch为true，则创建精确查询；否则创建模糊查询
     * 如果没有开启精准搜索配置，则为“content”以外的字段创建模糊查询和精准查询, 两种查询使用OR组合
     *
     * @param fieldNameExact 精准查询字段名
     * @param fieldNameFuzzy 模糊查询字段名
     * @param exactKeyword 精准查询关键字
     * @param fuzzyKeyword 模糊查询关键字
     * @param searchDTO 查询参数
     */
    private BooleanQuery.Builder getBooleanQueryFieldBuilder(String fieldNameExact, String fieldNameFuzzy, String exactKeyword, String fuzzyKeyword, SearchDTO searchDTO) throws ParseException {
        BooleanQuery.Builder booleanQueryFieldBuilder = new BooleanQuery.Builder();
        Query fuzzyQuery = null;
        Query  exactQuery = null;
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

    private Query getMultipleKeywordsQuery(SearchDTO searchDTO, String fuzzyKeyword) {
        if (fuzzyKeyword.contains(" ") && BooleanUtil.isTrue(searchDTO.getIncludeFileName())) {
            BooleanQuery.Builder nameAndTagMultipleWordsQueryBuilder = new BooleanQuery.Builder();
            for (String key : fuzzyKeyword.split(" ")) {

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

    private static Query getExactQuery(String fieldName, String exactSearchTerm) {
        Query contentQuery;
        if (CharSequenceUtil.isBlank(exactSearchTerm)) {
            contentQuery = new MatchNoDocsQuery();
        }  else if (exactSearchTerm.length() <= LuceneConfig.NGRAM_MAX_SIZE) {
            Term term = new Term(fieldName, exactSearchTerm);
            contentQuery = new TermQuery(term);
        } else {
            // 如果用户输入长度大于 maxGramSize，则分解查询字符串
            BooleanQuery.Builder decomposedQueryBuilder = new BooleanQuery.Builder();
            boolean hasValidSubTerms = false;

            // 生成重叠的、长度为 maxGram 的子串
            // 滑动窗口：从索引 0 开始，每次取长度为 maxGram 的子串，然后窗口向右移动一个字符
            for (int i = 0; i <= exactSearchTerm.length() - LuceneConfig.NGRAM_MAX_SIZE; i++) {
                String subTerm = exactSearchTerm.substring(i, i + LuceneConfig.NGRAM_MAX_SIZE);
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

    private static BooleanQuery getOtherOption(SearchDTO searchDTO) {
        BooleanQuery.Builder otherOptionBuilder = new BooleanQuery.Builder();
        // 文件类型
        if (CharSequenceUtil.isNotBlank(searchDTO.getType())) {
            otherOptionBuilder.add(new TermQuery(new Term("type", searchDTO.getType())), BooleanClause.Occur.MUST);
        }

        // 是否文件夹
        if (searchDTO.getIsFolder() != null) {
            otherOptionBuilder.add(IntPoint.newExactQuery("isFolder", searchDTO.getIsFolder() ? 1 : 0), BooleanClause.Occur.MUST);
        }

        // 更新时间范围查询
        if (searchDTO.getModifyStart() != null || searchDTO.getModifyEnd() != null) {
            long start = searchDTO.getModifyStart() != null ? searchDTO.getModifyStart() : Long.MIN_VALUE;
            long end = searchDTO.getModifyEnd() != null ? searchDTO.getModifyEnd() : Long.MAX_VALUE;
            otherOptionBuilder.add(NumericDocValuesField.newSlowRangeQuery("modified", start, end), BooleanClause.Occur.MUST);
        }

        // 文件大小范围查询
        if (searchDTO.getSizeMin() != null || searchDTO.getSizeMax() != null) {
            long minSize = searchDTO.getSizeMin() != null ? searchDTO.getSizeMin() : Long.MIN_VALUE;
            long maxSize = searchDTO.getSizeMax() != null ? searchDTO.getSizeMax() : Long.MAX_VALUE;
            otherOptionBuilder.add(NumericDocValuesField.newSlowRangeQuery("size", minSize, maxSize), BooleanClause.Occur.MUST);
        }
        return otherOptionBuilder.build();
    }

    /**
     * 获取挂载点查询
     *
     * @param searchDTO     searchDTO
     * @param combinedQuery 共用查询
     * @return mountQueryBuilder
     */
    private BooleanQuery getMountQueryBuilder(SearchDTO searchDTO, BooleanQuery combinedQuery, BooleanQuery otherQuery) {
        BooleanQuery.Builder mountQueryBuilder;
        if (BooleanUtil.isTrue(searchDTO.getSearchMount()) || searchDTO.onlySearchMount()) {
            mountQueryBuilder = new BooleanQuery.Builder();
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            query.addCriteria(Criteria.where("userId").is(searchDTO.getUserId()));
            query.addCriteria(Criteria.where("mountFileId").exists(true));
            query.fields().exclude("content");
            query.fields().exclude("video");
            query.fields().exclude("music");
            query.fields().exclude("contentText");
            query.fields().exclude("exif");
            List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class);
            fileDocumentList.parallelStream().forEach(fileDocument -> {
                BooleanQuery.Builder mountQueryBuilderItem = new BooleanQuery.Builder();
                org.springframework.data.mongodb.core.query.Query query1 = new org.springframework.data.mongodb.core.query.Query();
                query1.fields().include("userId");
                query1.fields().include("path");
                query1.fields().include("name");
                FileDocument fileBase = mongoTemplate.findById(fileDocument.getMountFileId(), FileDocument.class);
                if (fileBase != null) {
                    // userId
                    mountQueryBuilderItem.add(new TermQuery(new Term(IUserService.USER_ID, fileBase.getUserId())), BooleanClause.Occur.MUST);
                    // path
                    mountQueryBuilderItem.add(getPathQueryBuilder(fileBase.getPath() + fileBase.getName()), BooleanClause.Occur.MUST);
                    // other
                    mountQueryBuilderItem.add(combinedQuery, BooleanClause.Occur.MUST);
                    if (!otherQuery.clauses().isEmpty()) {
                        mountQueryBuilderItem.add(otherQuery, BooleanClause.Occur.MUST);
                    }
                }
                mountQueryBuilder.add(mountQueryBuilderItem.build(), BooleanClause.Occur.SHOULD);
            });
        } else {
            mountQueryBuilder = null;
        }
        return mountQueryBuilder == null ? null : mountQueryBuilder.build();
    }

    /**
     * 查询前置处理
     *
     * @param searchDTO searchDTO
     */
    private void beforeQuery(SearchDTO searchDTO) {
        String folder = searchDTO.getFolder();
        if (CharSequenceUtil.isNotBlank(folder)) {
            // 挂载点查询
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            query.addCriteria(Criteria.where("_id").is(folder));
            FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
            if (fileDocument != null && BooleanUtil.isFalse(searchDTO.getSearchOverall())) {
                searchDTO.setCurrentDirectory(fileDocument.getPath() + fileDocument.getName());
                searchDTO.setMountUserId(fileDocument.getUserId());
            }
        }
    }


    private void otherQueryParams(SearchDTO searchDTO, BooleanQuery.Builder builder) {
        boolean queryPath = CharSequenceUtil.isNotBlank(searchDTO.getCurrentDirectory()) && searchDTO.getCurrentDirectory().length() > 1;

        if (queryPath) {
            builder.add(getPathQueryBuilder(searchDTO.getCurrentDirectory()), BooleanClause.Occur.MUST);
        }

        // 标签
        if (searchDTO.getTagId() != null) {
            TagDO tagDO = tagService.getTagInfo(searchDTO.getTagId());
            if (tagDO != null) {
                builder.add(new RegexpQuery(new Term("tag", ".*" + tagDO.getName() + ".*")), BooleanClause.Occur.MUST);
            }
        }

        // 是否收藏
        if (searchDTO.getIsFavorite() != null && !queryPath) {
            builder.add(IntPoint.newExactQuery("isFavorite", searchDTO.getIsFavorite() ? 1 : 0), BooleanClause.Occur.MUST);
        }
    }

    private static BooleanQuery getPathQueryBuilder(String path) {
        // 检查path最后一个字符是否有/, 如果没有，则添加
        path = path.endsWith("/") ? path : path + "/";
        BooleanQuery.Builder pathQueryBuilder = new BooleanQuery.Builder();
        Term pathTerm = new Term("path", path);
        PrefixQuery prefixQuery = new PrefixQuery(pathTerm);
        pathQueryBuilder.add(new TermQuery(pathTerm), BooleanClause.Occur.SHOULD);
        pathQueryBuilder.add(prefixQuery, BooleanClause.Occur.SHOULD);
        return pathQueryBuilder.build();
    }

    public List<FileIntroVO> getFileIntroVOs(List<String> fileIdList) {
        List<ObjectId> objectIds = fileIdList.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();
        List<org.bson.Document> pipeline = Arrays.asList(new org.bson.Document("$match",
                        new org.bson.Document("_id",
                                new org.bson.Document("$in", objectIds))),
                new org.bson.Document("$project", new org.bson.Document("order", 0L).append("contentText", 0L).append("content", 0L)),
                new org.bson.Document("$addFields",
                        new org.bson.Document("order",
                                new org.bson.Document("$indexOfArray", Arrays.asList(objectIds, "$_id")))),
                new org.bson.Document("$sort",
                        new org.bson.Document("order", 1L))
                );

        AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(pipeline);

        List<FileIntroVO> results = new ArrayList<>();
        for (org.bson.Document document : aggregateIterable) {
            FileIntroVO fileIntroVO = mongoTemplate.getConverter().read(FileIntroVO.class, document);
            results.add(fileIntroVO);
        }
        return results;
    }

}
