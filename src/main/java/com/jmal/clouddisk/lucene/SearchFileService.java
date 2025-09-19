package com.jmal.clouddisk.lucene;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IFileQueryDAO;
import com.jmal.clouddisk.dao.ISearchHistoryDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.service.impl.TagService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jmal.clouddisk.lucene.LuceneService.FIELD_TAG_NAME_FUZZY;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchFileService {

    private final LuceneQueryService luceneQueryService;
    private final SearcherManager searcherManager;
    private final UserLoginHolder userLoginHolder;
    private final TagService tagService;
    private final ISearchHistoryDAO searchHistoryDAO;
    private final IFileDAO fileDAO;
    private final IFileQueryDAO fileQueryDAO;
    private final CommonUserService userService;

    private static final long HALF_YEAR_TIME = 182L * 24 * 60 * 60 * 1000;

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

            Query query = getQuery(searchDTO);
            searcherManager.maybeRefresh();

            Page<String> page = luceneQueryService.find(query, searchDTO);

            List<FileIntroVO> fileIntroVOList = fileQueryDAO.findAllFileIntroVOByIdIn(page.getContent());
            result.setData(fileIntroVOList);
            result.setCount(page.getTotalElements());

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
     *
     * @param searchUserId searchUserId
     * @param searchDTO    searchDTO
     */
    private void addSearchHistory(String searchUserId, SearchDTO searchDTO) {
        if (CharSequenceUtil.isBlank(searchUserId) || searchDTO == null) {
            return;
        }
        SearchOptionHistoryDO searchOptionHistoryDO = searchDTO.toSearchOptionDO();
        searchOptionHistoryDO.setSearchTime(System.currentTimeMillis());
        searchOptionHistoryDO.setUserId(searchUserId);
        // 删除重复的搜索记录
        searchHistoryDAO.removeByUserIdAndKeyword(searchDTO.getKeyword(), searchUserId);

        // 删除半年之前的搜索记录
        long time = System.currentTimeMillis() - HALF_YEAR_TIME;
        searchHistoryDAO.removeByUserIdAndSearchTimeLessThan(searchUserId, time);

        // 插入搜索记录
        searchHistoryDAO.save(searchOptionHistoryDO);
    }

    public List<SearchDTO> recentlySearchHistory(String keyword) {
        // 最近6条搜索记录
        Sort sort = Sort.by(Sort.Direction.DESC, "searchTime");
        Pageable pageable = PageRequest.of(0, 6, sort);
        List<SearchOptionHistoryDO> searchOptionHistoryDOList = searchHistoryDAO.findRecentByUserIdAndKeyword(userLoginHolder.getUserId(), keyword, pageable);
        return searchOptionHistoryDOList.stream().map(SearchOptionHistoryDO::toSearchDTO).toList();
    }

    public void deleteSearchHistory(String id) {
        if (CharSequenceUtil.isBlank(id)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        searchHistoryDAO.removeById(id);
    }

    public void deleteAllSearchHistory() {
        String userId = userLoginHolder.getUserId();
        if (CharSequenceUtil.isBlank(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        searchHistoryDAO.removeAllByUserId(userId);
    }

    /**
     * 构建查询器
     *
     * @param searchDTO searchDTO
     * @return Query
     * @throws ParseException ParseException
     */
    private Query getQuery(SearchDTO searchDTO) throws ParseException {
        BooleanQuery keywordQuery = luceneQueryService.getKeywordQuery(searchDTO);

        // 其他通用搜索选项
        BooleanQuery otherQuery = getOtherOption(searchDTO);

        // 创建最终查询（AND关系）
        BooleanQuery.Builder fileQueryBuilder = new BooleanQuery.Builder().add(new TermQuery(new Term(IUserService.USER_ID, searchDTO.getSearchUserId())), BooleanClause.Occur.MUST);

        if (!keywordQuery.clauses().isEmpty()) {
            fileQueryBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
        }

        if (!otherQuery.clauses().isEmpty()) {
            fileQueryBuilder.add(otherQuery, BooleanClause.Occur.MUST);
        }

        // 添加其他不通用查询条件
        otherQueryParams(searchDTO, fileQueryBuilder);

        // or 挂载点查询
        Query mountQuery = getMountQueryBuilder(searchDTO, keywordQuery, otherQuery);

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
            List<FileBaseDTO> fileBaseDTOList = fileDAO.findMountFileBaseDTOByUserId(searchDTO.getUserId());
            fileBaseDTOList.forEach(fileBaseDTO -> {
                BooleanQuery.Builder mountQueryBuilderItem = new BooleanQuery.Builder();
                if (fileBaseDTO != null) {
                    // userId
                    mountQueryBuilderItem.add(new TermQuery(new Term(IUserService.USER_ID, fileBaseDTO.getUserId())), BooleanClause.Occur.MUST);
                    // path
                    mountQueryBuilderItem.add(LuceneQueryService.getPathQueryBuilder(fileBaseDTO.getPath() + fileBaseDTO.getName()), BooleanClause.Occur.MUST);
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
            FileBaseDTO fileBaseDTO = fileDAO.findFileBaseDTOById(folder);
            if (fileBaseDTO != null && BooleanUtil.isFalse(searchDTO.getSearchOverall())) {
                searchDTO.setCurrentDirectory(fileBaseDTO.getPath() + fileBaseDTO.getName());
                searchDTO.setMountUserId(fileBaseDTO.getUserId());
            }
        }
    }


    private void otherQueryParams(SearchDTO searchDTO, BooleanQuery.Builder builder) {
        boolean queryPath = CharSequenceUtil.isNotBlank(searchDTO.getCurrentDirectory()) && searchDTO.getCurrentDirectory().length() > 1;

        if (queryPath) {
            builder.add(LuceneQueryService.getPathQueryBuilder(searchDTO.getCurrentDirectory()), BooleanClause.Occur.MUST);
        }

        // 标签
        if (searchDTO.getTagId() != null) {
            TagDO tagDO = tagService.getTagInfo(searchDTO.getTagId());
            if (tagDO != null) {
                builder.add(new TermQuery(new Term(FIELD_TAG_NAME_FUZZY, tagDO.getName())), BooleanClause.Occur.MUST);
            }
        }

        // 是否收藏
        if (searchDTO.getIsFavorite() != null && !queryPath) {
            builder.add(IntPoint.newExactQuery(Constants.IS_FAVORITE, searchDTO.getIsFavorite() ? 1 : 0), BooleanClause.Occur.MUST);
        }
    }

}
