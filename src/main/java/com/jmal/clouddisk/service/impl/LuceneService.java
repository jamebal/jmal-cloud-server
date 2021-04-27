package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description LuceneService
 * @Date 2021/4/27 4:44 下午
 */
@Service
@Slf4j
public class LuceneService {

    @Autowired(required = false)
    private IndexWriter indexWriter;

    @Autowired
    private Analyzer analyzer;

    @Autowired
    private SearcherManager searcherManager;

    @Autowired
    private IFileService fileService;

    public void createFileIndex(List<FileDocument> fileList) throws IOException {
        List<Document> docs = new ArrayList<>();
        for (FileDocument file : fileList) {
            Document doc = new Document();
            doc.add(new StringField("id", file.getId(), Field.Store.YES));
            doc.add(new TextField("name", file.getName(), Field.Store.YES));
            if (file.getContentText() != null){
                doc.add(new TextField("contentText", file.getContentText(), Field.Store.YES));
            }
            docs.add(doc);
        }
        indexWriter.addDocuments(docs);
        indexWriter.commit();
    }

    public ResponseResult<List<FileDocument>> searchFile(SearchDTO searchDTO) throws IOException, ParseException {
        searcherManager.maybeRefresh();
        IndexSearcher indexSearcher = searcherManager.acquire();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 模糊匹配,匹配词
        String keyword = searchDTO.getKeyword();
        if (!StringUtils.isEmpty(keyword)) {
            // 输入空格,不进行模糊查询
            if (!"".equals(keyword.replaceAll(" ", ""))) {
                builder.add(new QueryParser("name", analyzer).parse(keyword), BooleanClause.Occur.MUST);
            }
        }
        TopDocs topDocs = indexSearcher.search(builder.build(), 10);
        ScoreDoc[] hits = topDocs.scoreDocs;
        List<FileDocument> fileList = new ArrayList<>();
        for (ScoreDoc hit : hits) {
            Document doc = indexSearcher.doc(hit.doc);
            FileDocument fileDocument = new FileDocument();
            fileDocument.setId(doc.get("id"));
            fileDocument.setName(doc.get("name"));
            fileList.add(fileDocument);
        }
        return ResultUtil.success(fileList);
    }

    public void synFileCreatIndex() throws IOException {
        log.info("同步索引...");
        long startTime = System.currentTimeMillis();
        // 获取所有的file
        List<FileDocument> allProduct = fileService.getAllFile();
        // 再插入file
        createFileIndex(allProduct);
        log.info("同步索引耗时: {}ms", System.currentTimeMillis() - startTime);
    }
}
