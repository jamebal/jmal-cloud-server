package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
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
import com.jmal.clouddisk.util.StringUtil;
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

    public List<SearchDTO> recentlySearchHistory() {
        // 最近8条搜索记录
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("searchUserId").is(userLoginHolder.getUserId()));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "searchTime"));
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
        String[] fields = {"name", "tag", "content"};
        Map<String, Float> boosts = Map.of("name", 3.0f, "tag", 2.0f, "content", 1.0f);

        // 将关键字转为小写并去掉空格
        String keyword = searchDTO.getKeyword().toLowerCase().trim();

        // 将关键字中的特殊字符转义
        keyword = StringUtil.escape(keyword);

        // 创建正则表达式查询
        BooleanQuery.Builder regexpQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            if (!"content".equals(field)) {
                regexpQueryBuilder.add(new BoostQuery(new RegexpQuery(new Term(field, ".*" + keyword + ".*")), boosts.get(field)), BooleanClause.Occur.SHOULD);
            }
        }
        Query regExpQuery = new BoostQuery(regexpQueryBuilder.build(), 10.0f);

        // 对 content 字段进行完全匹配
        BooleanQuery.Builder contentQueryBuilder = new BooleanQuery.Builder();
        QueryParser contentParser = new QueryParser("content", analyzer);
        contentParser.setDefaultOperator(QueryParser.Operator.AND);
        Query contentQuery = contentParser.parse(keyword.trim());
        contentQueryBuilder.add(new BoostQuery(contentQuery, boosts.get("content")), BooleanClause.Occur.MUST);

        // 如何keyword中有空格，将其拆分为多个关键字，这多个关键字之间是AND关系,在name字段
        Query nameQuery = null;
        if (keyword.contains(" ")) {
            BooleanQuery.Builder nameQueryBuilder = new BooleanQuery.Builder();
            for (String key : keyword.split(" ")) {
                nameQueryBuilder.add(new BoostQuery(new RegexpQuery(new Term("name", ".*" + key + ".*")), 3.0f), BooleanClause.Occur.MUST);
            }
            nameQuery = nameQueryBuilder.build();
        }

        // 将正则表达式查询、短语查询和分词匹配查询组合成一个查询（OR关系）
        BooleanQuery.Builder combinedQueryBuilder = new BooleanQuery.Builder()
                .add(regExpQuery, BooleanClause.Occur.SHOULD)
                .add(contentQuery, BooleanClause.Occur.SHOULD);

        if (nameQuery != null) {
            combinedQueryBuilder.add(nameQuery, BooleanClause.Occur.SHOULD);
        }

        // 其他通用搜索选项
        BooleanQuery otherQuery = getOtherOption(searchDTO);

        BooleanQuery combinedQuery = combinedQueryBuilder.build();

        // 创建最终查询（AND关系）
        BooleanQuery.Builder fileQueryBuilder = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(IUserService.USER_ID, searchDTO.getUserId())), BooleanClause.Occur.MUST)
                .add(combinedQuery, BooleanClause.Occur.MUST);
        if (!otherQuery.clauses().isEmpty()) {
            fileQueryBuilder.add(otherQuery, BooleanClause.Occur.MUST);
        }

        // 添加其他不通用查询条件
        otherQueryParams(searchDTO, fileQueryBuilder);

        // or 挂载点查询
        Query mountQuery = getMountQueryBuilder(searchDTO, combinedQuery, otherQuery);

        // 构建最终查询
        BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder().add(fileQueryBuilder.build(), BooleanClause.Occur.SHOULD);
        if (mountQuery != null) {
            finalQueryBuilder.add(mountQuery, BooleanClause.Occur.SHOULD);
        }

        return finalQueryBuilder.build();
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
        if (BooleanUtil.isTrue(searchDTO.getSearchMount())) {
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
            if (fileDocument != null) {
                searchDTO.setCurrentDirectory(fileDocument.getPath() + fileDocument.getName());
                searchDTO.setUserId(fileDocument.getUserId());
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
        if (searchDTO.getIsFavorite() != null) {
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
                new org.bson.Document("$addFields",
                        new org.bson.Document("order",
                                new org.bson.Document("$indexOfArray", Arrays.asList(objectIds, "$_id")))),
                new org.bson.Document("$sort",
                        new org.bson.Document("order", 1L)),
                new org.bson.Document("$project",
                        new org.bson.Document("order", 0L)
                                .append("contentText", 0L)));

        AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(pipeline);

        List<FileIntroVO> results = new ArrayList<>();
        for (org.bson.Document document : aggregateIterable) {
            FileIntroVO fileIntroVO = mongoTemplate.getConverter().read(FileIntroVO.class, document);
            results.add(fileIntroVO);
        }
        return results;
    }

}
