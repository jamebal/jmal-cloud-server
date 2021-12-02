package com.jmal.clouddisk.service.impl;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                doc.add(new TextField("html", ReUtil.delAll("\\n", HtmlUtil.cleanHtmlTag(file.getHtml())), Field.Store.YES));
                docs.add(doc);
            }
        }
        indexWriter.addDocuments(docs);
        indexWriter.commit();
    }

    public ResponseResult<List<FileDocument>> searchFile(SearchDTO searchDTO) throws IOException, ParseException, InvalidTokenOffsetsException {
        // 模糊匹配,匹配词
        StringBuilder keyword = new StringBuilder(searchDTO.getKeyword());
        if (StrUtil.isBlank(keyword.toString())) {
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

            String[] keywords = StringUtil.escape(keyword.toString()).trim().replaceAll("\\s+", " ").split(" ");
            keyword = new StringBuilder(keywords[0] + (StringUtil.isShortStr(keywords[0]) ? "*" : ""));
            if (keywords.length > 1){
                for (int i = 1; i < keywords.length; i++) {
                    keyword.append(" OR ").append(keywords[i]).append(StringUtil.isShortStr(keywords[i]) ? "*" : "");
                }
            }
            Query query = parser.parse(keyword.toString());
            // 高亮格式，用<B>标签包裹
            Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<B>", "</B>"),
                    new QueryScorer(query));
            // 高亮后的段落范围在100字内
            Fragmenter fragmenter = new SimpleFragmenter(100);
            highlighter.setTextFragmenter(fragmenter);

            ScoreDoc[] hits = indexSearcher.search(query, 50).scoreDocs;
            for (ScoreDoc hit : hits) {
                Document doc = indexSearcher.doc(hit.doc);
                FileDocument fileDocument = new FileDocument();
                fileDocument.setId(doc.get("id"));
                String name = highlighter.getBestFragment(analyzer, fieldName, doc.get("name"));
                if (StrUtil.isBlank(name)) {
                    fileDocument.setName(doc.get("name"));
                } else {
                    fileDocument.setName(name);
                }
                String text = doc.get(fieldName);
                if (text != null) {
                    fileDocument.setContentText(highlighter.getBestFragment(analyzer, fieldName, text));
                }
                fileList.add(fileDocument);
            }
        } finally {
            searcherManager.release(indexSearcher);
        }
        return ResultUtil.success(fileList);
    }

    public static void main(String[] args) throws IOException {
        String content = "re redis token to本地端通讯需要在";
        Console.log(StringUtil.isContainChinese(content));
        //1.创建一个Analyzer对象
        Analyzer analyzer = new SmartChineseAnalyzer();
        //2.调用Analyzer对象的tokenStream方法获取TokenStream对象，此对象包含了所有的分词结果
        TokenStream tokenStream = analyzer.tokenStream("", content);
        //3.给tokenStream对象设置一个指针，指针在哪当前就在哪一个分词上
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        //4.调用tokenStream对象的reset方法，重置指针，不调用会报错
        tokenStream.reset();
        //5.利用while循环，拿到分词列表的结果  incrementToken方法返回值如果为false代表读取完毕  true代表没有读取完毕
        while (tokenStream.incrementToken()){
            String word = charTermAttribute.toString();
            System.out.println(word);
        }
        //6.关闭
        tokenStream.close();

    }

    public void synFileCreatIndex() throws IOException {
        IndexWriter.DocStats docStats = indexWriter.getDocStats();
        if (docStats.numDocs < 1) {
            log.info("同步Lucene索引... {}", docStats.numDocs);
            long startTime = System.currentTimeMillis();
            // 获取所有的file
            List<FileDocument> allProduct = fileService.getAllDocFile();
            // 再插入file
            createFileIndex(allProduct);
            log.info("同步Lucene索引耗时: {}ms", System.currentTimeMillis() - startTime);
        }
    }

    @PreDestroy
    public void destroy() throws IOException {
        searcherManager.close();
    }
}
