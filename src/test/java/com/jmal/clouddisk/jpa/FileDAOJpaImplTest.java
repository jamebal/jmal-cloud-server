package com.jmal.clouddisk.jpa;

import com.jmal.clouddisk.dao.impl.jpa.FileDAOJpaImpl;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.dto.FileBaseTagsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FileDAOJpaImpl的单元测试类。
 * 使用Mockito来模拟依赖，专注于测试DAO层的逻辑。
 */
@ExtendWith(MockitoExtension.class)
class FileDAOJpaImplTest {

    // --- Mocks for Dependencies ---
    @Mock
    private FileMetadataRepository fileMetadataRepository;
    @Mock
    private FilePropsRepository filePropsRepository;
    @Mock
    private LuceneQueryService luceneQueryService;
    @Mock
    private IWriteService writeService;

    // --- The Class Under Test ---
    @InjectMocks
    private FileDAOJpaImpl fileDAO;

    @BeforeEach
    void setUp() {
        // 配置所有返回CompletableFuture的mock方法，以避免NPE
        // 我们假设submit总是成功，除非在特定测试中另有规定
        lenient().when(writeService.submit(any(IDataOperation.class)))
                 .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Nested
    @DisplayName("Write Operations (using WriteService)")
    class WriteOperations {

        @Test
        @DisplayName("save() should submit a CreateFileMetadata operation")
        void save_shouldSubmitCreateFileMetadataOperation() {
            // Arrange
            FileDocument fileDocument = new FileDocument(); // 假设有默认构造
            fileDocument.setId("file1");
            // ... set other necessary properties for the constructor of FileMetadataDO

            // Act
            fileDAO.save(fileDocument);

            // Assert
            // 使用ArgumentCaptor捕获传递给writeService.submit的参数
            ArgumentCaptor<FileOperation.CreateFileMetadata> captor = ArgumentCaptor.forClass(FileOperation.CreateFileMetadata.class);
            verify(writeService).submit(captor.capture());

            // 验证捕获到的操作对象
            FileOperation.CreateFileMetadata submittedOperation = captor.getValue();
            assertThat(submittedOperation).isNotNull();
            assertThat(submittedOperation.entity()).isInstanceOf(FileMetadataDO.class);
            assertThat(submittedOperation.entity().getId()).isEqualTo("file1");
        }

        @Test
        @DisplayName("saveAll() should submit a CreateAllFileMetadata operation")
        void saveAll_shouldSubmitCreateAllFileMetadataOperation() {
            // Arrange
            FileDocument doc1 = new FileDocument(); doc1.setId("1");
            FileDocument doc2 = new FileDocument(); doc2.setId("2");
            List<FileDocument> documents = List.of(doc1, doc2);

            // Act
            fileDAO.saveAll(documents);

            // Assert
            ArgumentCaptor<FileOperation.CreateAllFileMetadata> captor = ArgumentCaptor.forClass(FileOperation.CreateAllFileMetadata.class);
            verify(writeService).submit(captor.capture());

            FileOperation.CreateAllFileMetadata operation = captor.getValue();
            assertThat(operation.entities()).hasSize(2);
            assertThat(operation.entities())
                .extracting("id") // Assuming FileMetadataDO has getId()
                .containsExactlyInAnyOrder("1", "2");
        }

        @Test
        @DisplayName("deleteAllByIdInBatch() should submit a DeleteAllByUserIdInBatch operation")
        void deleteAllByIdInBatch_shouldSubmitCorrectOperation() {
            // Arrange
            List<String> ids = List.of("user1", "user2");

            // Act
            fileDAO.deleteAllByIdInBatch(ids);

            // Assert
            ArgumentCaptor<FileOperation.DeleteAllByUserIdInBatch> captor = ArgumentCaptor.forClass(FileOperation.DeleteAllByUserIdInBatch.class);
            verify(writeService).submit(captor.capture());
            assertThat(captor.getValue().userIdList()).isEqualTo(ids);
        }

        @Test
        @DisplayName("updateTagInfoInFiles() should submit UpdateTagsForFile operations only for modified files")
        void updateTagInfoInFiles_shouldSubmitUpdateOperationsForModifiedFiles() {
            // Arrange
            String tagId = "tag123";
            List<String> affectedFileIds = List.of("file1", "file2", "file3");

            Tag tagToUpdate = new Tag(tagId, "Old Name", "blue");
            Tag otherTag = new Tag("otherTag", "Other", "red");

            FileBaseTagsDTO dto1 = new FileBaseTagsDTO("file1", List.of(tagToUpdate));
            FileBaseTagsDTO dto2 = new FileBaseTagsDTO("file2", List.of(otherTag)); // This one shouldn't be updated
            FileBaseTagsDTO dto3 = new FileBaseTagsDTO("file3", List.of(tagToUpdate, otherTag));

            when(luceneQueryService.findByTagId(tagId)).thenReturn(affectedFileIds);
            when(filePropsRepository.findTagsByIdIn(affectedFileIds)).thenReturn(List.of(dto1, dto2, dto3));

            // Act
            fileDAO.updateTagInfoInFiles(tagId, "New Name", "green");

            // Assert
            // 验证只有dto1和dto3触发了submit
            ArgumentCaptor<FileOperation.UpdateTagsForFile> captor = ArgumentCaptor.forClass(FileOperation.UpdateTagsForFile.class);
            verify(writeService, times(2)).submit(captor.capture());

            List<FileOperation.UpdateTagsForFile> operations = captor.getAllValues();
            assertThat(operations).extracting("fileId").containsExactlyInAnyOrder("file1", "file3");

            // 检查被更新的tag内容是否正确
            Tag updatedTagInOp1 = operations.stream()
                .filter(op -> op.fileId().equals("file1"))
                .flatMap(op -> op.tags().stream())
                .findFirst().get();

            assertThat(updatedTagInOp1.getName()).isEqualTo("New Name");
            assertThat(updatedTagInOp1.getColor()).isEqualTo("green");
        }
    }

    @Nested
    @DisplayName("Read and Mixed Operations")
    class ReadAndMixedOperations {

        @Test
        @DisplayName("upsertMountFile() when file exists, should find and then submit an update")
        void upsertMountFile_whenFileExists_shouldUpdate() throws Exception {
            // Arrange
            FileDocument doc = new FileDocument();
            doc.setName("test.txt"); doc.setUserId("user1"); doc.setPath("/");

            FileMetadataDO existingFile = new FileMetadataDO(doc);
            existingFile.setId("existingId");

            when(fileMetadataRepository.findByNameAndUserIdAndPath("test.txt", "user1", "/"))
                .thenReturn(Optional.of(existingFile));
            // 模拟带返回值的submit
            when(writeService.submit(any(FileOperation.CreateFileMetadata.class)))
                .thenReturn(CompletableFuture.completedFuture(existingFile));

            // Act
            String resultId = fileDAO.upsertMountFile(doc);

            // Assert
            assertThat(resultId).isEqualTo("existingId");

            ArgumentCaptor<FileOperation.CreateFileMetadata> captor = ArgumentCaptor.forClass(FileOperation.CreateFileMetadata.class);
            verify(writeService).submit(captor.capture());
            // 验证提交的是被找到的那个实体，而不是新创建的
            assertThat(captor.getValue().entity()).isSameAs(existingFile);
        }

        @Test
        @DisplayName("findAllAndRemoveByUserIdAndIdPrefix() should find, submit delete, and return documents")
        void findAllAndRemove_shouldWorkAsExpected() {
            // Arrange
            String userId = "user1";
            String idPrefix = "/docs/";
            FileMetadataDO file1 = new FileMetadataDO(); file1.setId("/docs/file1.txt");
            FileMetadataDO file2 = new FileMetadataDO(); file2.setId("/docs/file2.txt");
            List<FileMetadataDO> filesToDelete = List.of(file1, file2);

            when(fileMetadataRepository.findAllByUserIdAndIdPrefix(userId, idPrefix + "%"))
                .thenReturn(filesToDelete);

            // Act
            List<FileDocument> result = fileDAO.findAllAndRemoveByUserIdAndIdPrefix(userId, idPrefix);

            // Assert
            // 1. 验证返回结果是正确的
            assertThat(result).hasSize(2);
            assertThat(result).extracting("id").containsExactlyInAnyOrder("/docs/file1.txt", "/docs/file2.txt");

            // 2. 验证异步删除被正确调用
            ArgumentCaptor<FileOperation.DeleteAllByIdInBatch> captor = ArgumentCaptor.forClass(FileOperation.DeleteAllByIdInBatch.class);
            verify(writeService).submit(captor.capture());
            assertThat(captor.getValue().fileIdList()).containsExactlyInAnyOrder("/docs/file1.txt", "/docs/file2.txt");
        }

        // 可以为其他的只读方法添加测试，但它们通常直接调用repository，测试价值相对较低
        // 比如，下面是一个简单的例子
        @Test
        @DisplayName("existsById() should delegate to repository")
        void existsById_shouldDelegateToRepository() {
            // Arrange
            String fileId = "file1";
            when(fileMetadataRepository.existsById(fileId)).thenReturn(true);

            // Act
            boolean exists = fileDAO.existsById(fileId);

            // Assert
            assertThat(exists).isTrue();
            verify(fileMetadataRepository).existsById(fileId); // 验证方法被调用
        }
    }
}

// 注意: 为了让这个测试能编译通过，你可能需要为FileDocument, Tag, FileTagsDTO等
// 添加默认构造函数或确保测试中设置了所有必要的属性。
