package com.jmal.clouddisk.service.impl;

import cn.hutool.http.HtmlUtil;
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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.highlight.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;

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
            if (file.getHtml() != null) {
                doc.add(new TextField("html", file.getHtml(), Field.Store.YES));
            }
            docs.add(doc);
        }
        indexWriter.addDocuments(docs);
        indexWriter.commit();
    }

    public ResponseResult<List<FileDocument>> searchFile(SearchDTO searchDTO) throws IOException, ParseException, InvalidTokenOffsetsException {
        // 模糊匹配,匹配词
        String keyword = searchDTO.getKeyword();
        if (StringUtils.isEmpty(keyword)) {
            return ResultUtil.success();
        }
        searcherManager.maybeRefresh();
        IndexSearcher indexSearcher = searcherManager.acquire();
        List<FileDocument> fileList = new ArrayList<>();
        String fieldName = "html";
        try {
            String[] fields = new String[]{fieldName, "name"};
            Map<String, Float> boots = new HashMap<>(5);
            boots.put(fieldName, 10.0f);
            boots.put("name", 2.0f);


            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boots);
            Query query = parser.parse(keyword);
            // 高亮格式，用<B>标签包裹
            Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<B>", "</B>"),
                    new QueryScorer(query));
            // 高亮后的段落范围在100字内
            Fragmenter fragmenter = new SimpleFragmenter(50);
            highlighter.setTextFragmenter(fragmenter);

            ScoreDoc[] hits = indexSearcher.search(query, 5).scoreDocs;
            for (ScoreDoc hit : hits) {
                Document doc = indexSearcher.doc(hit.doc);
                FileDocument fileDocument = new FileDocument();
                fileDocument.setId(doc.get("id"));
                fileDocument.setName(doc.get("name"));
                String text = doc.get(fieldName);
                if (text != null) {
                    fileDocument.setContentText(highlighter.getBestFragment(analyzer, fieldName, HtmlUtil.cleanHtmlTag(text)));
                }
                fileList.add(fileDocument);
            }
        } finally {
            searcherManager.release(indexSearcher);
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

    @PreDestroy
    public void destroy() throws IOException {
        searcherManager.close();
    }
}
