package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.impl.LuceneService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
public class LuceneSearchController {

    @Autowired
    private LuceneService luceneService;

    @Operation(summary = "搜索")
    @GetMapping("/")
    public ResponseResult<List<FileDocument>> list(SearchDTO searchDTO) throws IOException, ParseException, InvalidTokenOffsetsException {
        return luceneService.searchFile(searchDTO);
    }
}
