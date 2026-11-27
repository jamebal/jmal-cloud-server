package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.oss.web.WebOssCopyFileService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.ShortSignedIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileTransferAccessTest {
    private static final String BOB_OSS = "/bob/transfer-test-bucket";
    private static final String ALICE_OSS = "/alice/transfer-test-bucket";

    @Mock private UserLoginHolder login;
    @Mock private CommonFileService common;
    @Mock private CommonUserService users;
    @Mock private FileProperties properties;
    @Mock private IFileDAO files;
    @Mock private IShareDAO shares;
    @Mock private LogService logs;
    @Mock private WebOssCopyFileService ossCopy;
    @Mock private WebOssService oss;
    @InjectMocks private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        when(login.getUserId()).thenReturn("bob-id");
        when(login.getUsername()).thenReturn("bob");
    }

    @AfterEach
    void removeOssRoutes() {
        CaffeineUtil.removeOssDiameterPrefixCache(BOB_OSS);
        CaffeineUtil.removeOssDiameterPrefixCache(ALICE_OSS);
    }

    @Test
    void copyChecksSourceAgainstSessionNotRequestOrBarePermissionMetadata() {
        FileDocument foreign = document("alice-file", "alice-id");
        foreign.setOperationPermissionList(List.of(OperationPermission.DOWNLOAD));
        when(common.getFileDocumentById("alice-file", true)).thenReturn(foreign);
        UploadApiParamDTO upload = new UploadApiParamDTO();
        upload.setUserId("alice-id");
        upload.setUsername("alice");

        assertDenied(() -> service.copy(upload, List.of("alice-file"), Constants.REGION_DEFAULT, null));
        assertDenied(() -> service.checkMoveOrCopy(upload, List.of("alice-file"), Constants.REGION_DEFAULT, null));
        verifyNoInteractions(shares);
    }

    @Test
    void moveChecksTheEntireBatchBeforeSchedulingAnyWork() {
        when(common.getFileDocumentById("bob-file", true)).thenReturn(document("bob-file", "bob-id"));
        when(common.getFileDocumentById("alice-file", true)).thenReturn(document("alice-file", "alice-id"));

        assertDenied(() -> service.move(new UploadApiParamDTO(), List.of("bob-file", "alice-file"),
                Constants.REGION_DEFAULT, null));
    }

    @Test
    void mountOwnerMustAlsoBeAuthorizedToCopyTheTarget() {
        FileDocument mount = document("bob-mount", "bob-id");
        mount.setMountFileId("alice-folder");
        when(common.getFileDocumentById("bob-mount", true)).thenReturn(mount);
        when(common.getFileDocumentById("alice-folder", true)).thenReturn(document("alice-folder", "alice-id"));

        assertDenied(() -> service.copy(new UploadApiParamDTO(), List.of("bob-mount"), Constants.REGION_DEFAULT, null));
    }

    @Test
    void rawOssSourceAndDestinationAreScopedToTheCaller() {
        CaffeineUtil.setOssDiameterPrefixCache(ALICE_OSS, mock(BucketInfo.class));
        assertDenied(() -> service.copy(new UploadApiParamDTO(), List.of(ALICE_OSS + "/file"), Constants.REGION_DEFAULT, null));
        when(common.getFileDocumentById("bob-file", true)).thenReturn(document("bob-file", "bob-id"));
        when(common.getFileDocumentById(ALICE_OSS + "/folder/", true)).thenReturn(null);
        assertDenied(() -> service.copy(new UploadApiParamDTO(), List.of("bob-file"), ALICE_OSS + "/folder/", null));
    }

    @Test
    void activePrivateShareAllowsCopyWithTokenButNotMoveWithoutDeletePermission() {
        CaffeineUtil.setOssDiameterPrefixCache(BOB_OSS, mock(BucketInfo.class));
        FileDocument foreign = document("alice-file", "alice-id");
        foreign.setIsShare(true);
        foreign.setExpiresAt(Long.MAX_VALUE);
        foreign.setShareId("share1");
        when(common.getFileDocumentById("alice-file", true)).thenReturn(foreign);
        ShareDO share = new ShareDO();
        share.setId("share1");
        share.setUserId("alice-id");
        share.setIsPrivacy(true);
        share.setExtractionCode("test");
        share.setOperationPermissionList(List.of(OperationPermission.DOWNLOAD));
        when(shares.findById("share1")).thenReturn(share);
        String target = BOB_OSS + "/folder/";
        when(common.getFileDocumentById(target, true)).thenReturn(null);
        String token = ShortSignedIdUtil.generateToken(share.getId(), share.getExtractionCode());

        assertDenied(() -> service.copy(new UploadApiParamDTO(), List.of("alice-file"), target, null));
        assertDenied(() -> service.move(new UploadApiParamDTO(), List.of("alice-file"), target, token));

        when(users.getUserNameById("alice-id")).thenReturn("alice");
        when(ossCopy.copyLocalToOss(BOB_OSS, "alice-file", "alice", target, false)).thenReturn(ResultUtil.success());
        assertEquals(0, service.copy(new UploadApiParamDTO(), List.of("alice-file"), target, token).getCode());
        verify(ossCopy, timeout(3000)).copyLocalToOss(BOB_OSS, "alice-file", "alice", target, false);
    }

    @Test
    void conflictPreviewOnlyReturnsFieldsNeededForTheConflict() {
        FileDocument source = document("bob-file", "bob-id");
        source.setName("file.txt");
        FileDocument existing = document("existing-file", "bob-id");
        existing.setName("file.txt");
        existing.setPath("/");
        existing.setSuffix("txt");
        existing.setContentText("not needed for the preview");
        existing.setExtractionCode("test");
        when(common.getFileDocumentById(source.getId(), true)).thenReturn(source);
        when(properties.getSeparator()).thenReturn("/");
        when(files.findAllByUserIdAndPathAndNameIn("bob-id", "/", List.of("file.txt"))).thenReturn(List.of(existing));

        FileDocument conflict = service.checkMoveOrCopy(new UploadApiParamDTO(), List.of(source.getId()),
                Constants.REGION_DEFAULT, null).getData().getFirst();
        assertEquals("file.txt", conflict.getName());
        assertNull(conflict.getContentText());
        assertNull(conflict.getExtractionCode());
    }

    @Test
    void ownerCanStillCopyTheirLocalFile(@TempDir Path root) throws Exception {
        FileDocument source = document("bob-file", "bob-id");
        source.setPath("/in/");
        source.setName("file.txt");
        Path original = root.resolve("bob/in/file.txt");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "content");
        when(common.getFileDocumentById(source.getId(), true)).thenReturn(source);
        when(users.getUserNameById("bob-id")).thenReturn("bob");
        when(properties.getRootDir()).thenReturn(root.toString());
        when(properties.getSeparator()).thenReturn("/");
        UploadApiParamDTO upload = new UploadApiParamDTO();
        upload.setIsOverride(true);

        assertEquals(0, service.copy(upload, List.of(source.getId()), Constants.REGION_DEFAULT, null).getCode());
        verify(common, timeout(3000)).pushMessageOperationFileSuccess(
                anyString(), anyString(), eq("bob"), eq("复制"));
        assertEquals("content", Files.readString(root.resolve("bob/file.txt")));
        assertTrue(Files.exists(original));
    }

    @Test
    void ownerCanMoveTheirOssObjectAndOnlyThatSourceIsCleanedUp() {
        CaffeineUtil.setOssDiameterPrefixCache(BOB_OSS, mock(BucketInfo.class));
        String source = BOB_OSS + "/file";
        when(users.getUserNameById("bob-id")).thenReturn("bob");
        when(ossCopy.copyOssToLocal(BOB_OSS, source, Constants.REGION_DEFAULT, "bob", true)).thenReturn(ResultUtil.success());

        assertEquals(0, service.move(new UploadApiParamDTO(), List.of(source), Constants.REGION_DEFAULT, null).getCode());
        verify(ossCopy, timeout(3000)).copyOssToLocal(BOB_OSS, source, Constants.REGION_DEFAULT, "bob", true);
        verify(oss, timeout(3000)).delete(BOB_OSS, List.of(source));
    }

    private void assertDenied(Executable action) {
        assertEquals(ExceptionType.PERMISSION_DENIED.getCode(), assertThrows(CommonException.class, action).getCode());
        verifyNoInteractions(logs, properties, files, ossCopy, oss);
    }

    private static FileDocument document(String id, String owner) {
        FileDocument file = new FileDocument();
        file.setId(id);
        file.setUserId(owner);
        return file;
    }
}
