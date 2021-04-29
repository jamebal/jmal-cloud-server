/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jmal.clouddisk.lucene;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/**
 * 为一个目录下的所有文本文件编制索引
 * 这是一个演示简单Lucene索引的命令行应用程序
 * 在不使用命令行参数的情况下运行它以获取使用情况信息
 */
public class IndexFiles {

    /**
     * 索引目录
     */
    public static final String INDEX_PATH = "/Users/jmal/temp/lucene/index";
    /**
     * 文档目录
     */
    public static final String DOCS_PATH = "/Users/jmal/temp/filetest/rootpath/jmal/Document";

    private IndexFiles() {
    }

    /**
     * 为一个目录下的所有文本文件编制索引
     */
    public static void main(String[] args) {
        String usage = "com.jmal.clouddisk.lucene.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "这将对DOCS_PATH中的文档进行索引，在INDEX_PATH中创建一个Lucene索引，"
                + "可以用SearchFiles进行搜索。";

        String indexPath = "index";
        String docsPath = null;
        boolean create = true;
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i + 1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            }
        }

        if (docsPath == null) {
            // 手动赋值
            // INDEX_PATH
            indexPath = INDEX_PATH;
            // DOCS_PATH
            docsPath = DOCS_PATH;
            // System.err.println("使用方法: " + usage);
            // System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("文档目录 '" + docDir.toAbsolutePath() + "' 不存在或不可读，请检查路径");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("对目录 '" + indexPath + "' 编制索引... ");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new SmartChineseAnalyzer(true);
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                // 在目录中创建一个新的索引，删除任何先前索引的文件
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // 将新的文件添加到现有的索引中
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // 可选：为了获得更好的索引性能，如果你要为许多文档建立索引，可以增加RAM缓冲区
            // 但是，如果你这样做，请增加JVM的最大堆大小（例如添加-Xmx512m或-Xmx1g）
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);

            // 注意：如果你想最大限度地提高搜索性能，你可以在这里选择性地调用forceMerge
            // 这可能是一个非常昂贵的操作，所以一般来说，
            // 只有当你的索引是相对静态的（即你已经完成了对它的添加），才值得这样做
            // writer.forceMerge(1);
            writer.close();
            Date end = new Date();
            System.out.println("编制索引耗时：" + (end.getTime() - start.getTime()) + " ms");

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }
    }

    /**
     * 使用给定的写入器对给定的文件进行索引，如果给定的是目录，
     * 则对在给定目录下找到的文件和目录进行检索。
     * 注意：该方法为每个输入文件索引一个文件，这很慢。
     * 为了获得良好的吞吐量，将多个文件放入你的输入文件。
     * 这方面的一个例子是在基准模块中，
     * 它可以使用WriteLineDocTask创建 "line doc" 文件，每行一个文档。
     *
     * @param writer 写入索引，给定的文件/目录信息将被存储在那里
     * @param path   要索引的文件，或要重新搜索的目录，以找到要索引的文件
     * @throws IOException 如果有一个低级别的I/O错误
     */
    static void indexDocs(final IndexWriter writer, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                    } catch (IOException ignore) {
                        // 不要索引不能被读取的文件
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    /**
     * 为单个文件编制索引
     */
    static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // 创建一个新的的文档
            Document doc = new Document();

            // 添加文件的路径作为一个名为 "path "的字段。 使用一个
            // 字段是可以被索引的（即可以搜索），但不要将字段标记为独立的词，也不要将词频索引
            // 变成独立的字段，并且不对术语频率进行索引，或位置信息
            Field pathField = new StringField("path", file.toString(), Field.Store.YES);
            doc.add(pathField);

            // 将文件的最后修改日期添加到一个名为 "modified "的字段中
            // 使用一个有索引的LongPoint（即可以有效地用PointRangeQuery过滤）
            // 这个索引的分辨率为毫秒级，这往往过于精细
            // 你可以创建一个基于以下数据的数字
            // 年/月/日/小时/分钟/秒，并降低你所需要的分辨率
            // 例如，长值2021021714意味着 2021年2月17日，下午2-3点
            doc.add(new LongPoint("modified", lastModified));

            //  将文件的内容添加到一个名为 "contents" 的字段中
            //  指定一个阅读器，以便文件的文本被标记和索引，但不存储
            //  注意，FileReader希望文件是UTF-8编码的
            //  如果不是这样，搜索特殊字符将失败
            doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));

            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                // 新的索引，所以我们只需添加文件
                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                // 现有的索引（这个文件的一个旧的副本可能已经被索引了），
                // 所以我们使用updateDocument来代替旧的索引，
                // 如果存在的话，匹配准确的路径。
                System.out.println("updating " + file);
                writer.updateDocument(new Term("path", file.toString()), doc);
            }
        }
    }
}
