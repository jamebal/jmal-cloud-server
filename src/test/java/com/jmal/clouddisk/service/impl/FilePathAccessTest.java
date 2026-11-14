package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.util.CaffeineUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilePathAccessTest {
    @Mock private UserLoginHolder login;
    @Mock private FileProperties properties;
    @Mock private CommonFileService common;
    @Mock private WebOssService oss;
    @Mock private IFileDAO fileDAO;
    @Mock private LogService logs;
    @InjectMocks private FileServiceImpl service;
    @TempDir Path root;

    @BeforeEach
    void setUp() {
        lenient().when(login.getUsername()).thenReturn("alice");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bob", " "})
    void rejectsForeignOrMissingOwnerBeforeAnyStorageAccess(String username) throws Exception {
        Path secret = root.resolve("bob/secret.txt");
        Files.createDirectories(secret.getParent());
        Files.writeString(secret, "victim secret");
        CommonException deletion = assertThrows(CommonException.class,
                () -> service.delFile("/secret.txt", username));
        assertEquals(ExceptionType.PERMISSION_DENIED.getCode(), deletion.getCode());
        for (boolean temp : new boolean[]{false, true}) {
            CommonException listing = assertThrows(CommonException.class,
                    () -> service.listFiles("/", username, temp));
            assertEquals(ExceptionType.PERMISSION_DENIED.getCode(), listing.getCode());
        }
        assertEquals("victim secret", Files.readString(secret));
        verifyNoInteractions(properties, common, oss, fileDAO, logs);
    }

    @Test
    void rejectsForeignOssListingBeforeDispatch() {
        String prefix = "/bob/path-access-bucket";
        CaffeineUtil.setOssDiameterPrefixCache(prefix, mock(BucketInfo.class));
        try {
            assertThrows(CommonException.class,
                    () -> service.listFiles("/path-access-bucket/", "bob", true));
            verifyNoInteractions(oss);
        } finally {
            CaffeineUtil.removeOssDiameterPrefixCache(prefix);
        }
    }

    @Test
    void ownerCanListStagedFiles() throws Exception {
        when(properties.getRootDir()).thenReturn(root.toString());
        when(properties.getChunkFileDir()).thenReturn("chunks");
        Path staged = root.resolve("chunks/alice/upload.bin");
        Files.createDirectories(staged.getParent());
        Files.writeString(staged, "chunk");
        List<?> result = (List<?>) service.listFiles("/", "alice", true).getData();
        assertEquals(1, result.size());
        assertEquals("upload.bin", ((FileIntroVO) result.getFirst()).getName());
    }

    @Test
    void ownerCanDeleteOwnFile() throws Exception {
        when(properties.getRootDir()).thenReturn(root.toString());
        Path own = root.resolve("alice/own.txt");
        Files.createDirectories(own.getParent());
        Files.writeString(own, "own content");
        service.delFile("/own.txt", "alice");
        assertFalse(Files.exists(own));
        verify(common).deleteFile("alice", own.toFile());
        verify(logs).asyncAddLogFileOperation("alice", "/own.txt", "彻底删除文件");
    }
}
