package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.impl.TagService;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchFileServiceTest {
    @Test
    void expandsTaggedAndFavoriteFoldersOnlyAtRoot() throws Exception {
        try (var directory = new ByteBuffersDirectory();
             var analyzer = new KeywordAnalyzer()) {
            List<FileBaseDTO> files = new ArrayList<>();
            try (var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                add(writer, files, "folder", "u", "/", "marked", true, true, true, "other");
                add(writer, files, "child", "u", "/marked/", "child", false, false, false, "hit");
                add(writer, files, "deep", "u", "/marked/nested/", "deep", false, false, false, "hit");
                add(writer, files, "direct", "u", "/", "direct", false, true, true, "hit");
                add(writer, files, "sibling", "u", "/marked-other/", "sibling", false, false, false, "hit");
                add(writer, files, "wrongKeyword", "u", "/marked/", "wrong", false, false, false, "other");
                add(writer, files, "foreignFolder", "other", "/", "foreign", true, true, true, "other");
                add(writer, files, "foreignChild", "other", "/marked/", "child", false, false, false, "hit");
                add(writer, files, "samePath", "u", "/foreign/", "child", false, false, false, "hit");
            }
            try (var reader = DirectoryReader.open(directory)) {
                var searcher = new IndexSearcher(reader);
                var lucene = mock(LuceneQueryService.class);
                var dao = mock(IFileDAO.class);
                var tags = mock(TagService.class);
                var tag = new TagDO();
                tag.setName("tag");
                when(tags.getTagInfo("tag-id")).thenReturn(tag);
                when(lucene.getKeywordQuery(any())).thenReturn(new BooleanQuery.Builder()
                        .add(new TermQuery(new Term("keyword", "hit")), BooleanClause.Occur.MUST).build());
                when(lucene.find(any(Query.class))).thenAnswer(invocation -> ids(searcher, invocation.getArgument(0)));
                when(dao.findAllFileBaseDTOByIdIn(anyList())).thenAnswer(invocation -> {
                    List<String> ids = invocation.getArgument(0);
                    return files.stream().filter(file -> ids.contains(file.getId())).toList();
                });
                var service = new SearchFileService(lucene, null, tags, null, dao, null, null);
                for (String root : new String[]{null, "", "/"}) {
                    assertThat(search(service, searcher, SearchDTO.builder().userId("u").currentDirectory(root)
                            .tagId("tag-id").build())).containsExactlyInAnyOrder("child", "deep", "direct");
                    assertThat(search(service, searcher, SearchDTO.builder().userId("u").currentDirectory(root)
                            .isFavorite(true).build())).containsExactlyInAnyOrder("child", "deep", "direct");
                }
                assertThat(search(service, searcher, SearchDTO.builder().userId("u").currentDirectory("/marked")
                        .tagId("tag-id").isFavorite(true).build())).containsExactlyInAnyOrder("child", "deep");
                assertThat(search(service, searcher, SearchDTO.builder().userId("u").isFavorite(false).build()))
                        .containsExactlyInAnyOrder("child", "deep", "sibling", "samePath");
                assertThat(search(service, searcher, SearchDTO.builder().userId("u").tagId("tag-id")
                        .isFavorite(true).isFolder(true).build())).isEmpty();
                assertThat(search(service, searcher, SearchDTO.builder().userId("nobody").isFavorite(true).build()))
                        .isEmpty();
            }
        }
    }

    private static List<String> search(SearchFileService service, IndexSearcher searcher, SearchDTO dto) throws Exception {
        Query query = ReflectionTestUtils.invokeMethod(service, "getQuery", dto);
        return ids(searcher, query);
    }

    private static List<String> ids(IndexSearcher searcher, Query query) throws Exception {
        List<String> ids = new ArrayList<>();
        for (var hit : searcher.search(query, 100).scoreDocs) {
            ids.add(searcher.storedFields().document(hit.doc).get("id"));
        }
        return ids;
    }

    private static void add(IndexWriter writer, List<FileBaseDTO> files, String id, String userId,
                            String path, String name, boolean folder, boolean tagged, boolean favorite,
                            String keyword) throws Exception {
        files.add(new FileBaseDTO(id, name, path, userId, folder));
        var document = new Document();
        document.add(new StringField("id", id, Field.Store.YES));
        document.add(new StringField("userId", userId, Field.Store.NO));
        document.add(new StringField("path", path, Field.Store.NO));
        document.add(new StringField("keyword", keyword, Field.Store.NO));
        document.add(new IntPoint("isFolder", folder ? 1 : 0));
        document.add(new IntPoint("isFavorite", favorite ? 1 : 0));
        if (tagged) {
            document.add(new StringField(LuceneService.FIELD_TAG_NAME_FUZZY, "tag", Field.Store.NO));
        }
        writer.addDocument(document);
    }
}
