package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.lucene.SearchFileService;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * @author jmal
 * @Description 全文搜索
 * @Date 2021/4/27 5:17 下午
 */
@RestController
@RequestMapping("/search")
@Tag(name = "全文搜索")
@RequiredArgsConstructor
public class LuceneSearchController {

    private final SearchFileService searchFileService;

    @Operation(summary = "搜索")
    @GetMapping("/")
    public ResponseResult<List<FileIntroVO>> list(@Valid SearchDTO searchDTO) throws IOException, ParseException, InvalidTokenOffsetsException {
        return searchFileService.searchFile(searchDTO);
    }

    @Operation(summary = "最近搜索记录")
    @GetMapping("/recentlySearchHistory")
    public ResponseResult<List<SearchDTO>> recentlySearchHistory(String keyword) {
        return ResultUtil.success(searchFileService.recentlySearchHistory(keyword));
    }

    @Operation(summary = "删除搜索记录")
    @DeleteMapping("/deleteSearchHistory")
    public ResponseResult<String> deleteSearchHistory(String id) {
        searchFileService.deleteSearchHistory(id);
        return ResultUtil.success();
    }

    @Operation(summary = "删除所有搜索记录")
    @DeleteMapping("/deleteAllSearchHistory")
    public ResponseResult<String> deleteAllSearchHistory() {
        searchFileService.deleteAllSearchHistory();
        return ResultUtil.success();
    }
}
