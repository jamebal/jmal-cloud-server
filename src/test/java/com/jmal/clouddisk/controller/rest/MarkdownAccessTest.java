package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarkdownAccessTest {
    @TempDir Path root;
    private final CommonFileService common = mock(CommonFileService.class);
    private final IUserService users = mock(IUserService.class);
    private final UserLoginHolder login = mock(UserLoginHolder.class);
    private final FileInterceptor access = mock(FileInterceptor.class);
    private final WebOssService oss = mock(WebOssService.class);
    private final LogService logs = mock(LogService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private MarkdownServiceImpl markdown;
    private MarkDownController controller;
    private Path victim;

    @BeforeEach
    void setUp() throws Exception {
        when(login.getUsername()).thenReturn("bob");
        when(login.getUserId()).thenReturn("bob-id");
        when(users.getUserIdByUserName("alice")).thenReturn("alice-id");
        when(users.getUserNameById("alice-id")).thenReturn("alice");
        ReflectionTestUtils.setField(common, "userLoginHolder", login);
        doCallRealMethod().when(common).checkPermissionUsername(anyString(), any(), any());
        FileProperties properties = new FileProperties();
        properties.setRootDir(root.toString());
        markdown = new MarkdownServiceImpl(users, properties, null, null, null, common, null,
                events, null, logs, login, null, null);
        controller = new MarkDownController(markdown, oss, common, users, access, login, new MockHttpServletRequest());
        victim = root.resolve("alice/payroll.txt");
        Files.createDirectories(victim.getParent());
        Files.writeString(victim, "PRIVATE ORIGINAL");
    }

    private UploadApiParamDTO attack() {
        UploadApiParamDTO upload = new UploadApiParamDTO();
        upload.setUsername("alice");
        upload.setUserId("bob-id");
        upload.setRelativePath("/payroll.txt");
        upload.setContentText("OVERWRITTEN");
        return upload;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsForgedOwnerAndClientPermissionsWithoutWriting(boolean forgedPermissions) throws Exception {
        UploadApiParamDTO upload = attack();
        if (forgedPermissions) upload.setOperationPermissionList(List.of(OperationPermission.PUT));
        assertThrows(CommonException.class, () -> controller.editTextByPath(upload));
        assertEquals("PRIVATE ORIGINAL", Files.readString(victim));
        verifyNoInteractions(oss, logs, events);
    }

    @Test
    void ossWriteCannotSkipOwnershipCheck() {
        String prefix = "/alice/access-test-bucket";
        CaffeineUtil.setOssDiameterPrefixCache(prefix, mock(BucketInfo.class));
        try {
            UploadApiParamDTO upload = attack();
            upload.setRelativePath("/access-test-bucket/payroll.txt");
            assertThrows(CommonException.class, () -> controller.editTextByPath(upload));
            verifyNoInteractions(oss, logs, events);
        } finally {
            CaffeineUtil.removeOssDiameterPrefixCache(prefix);
        }
    }

    @Test
    void serviceChecksActualUsernameRatherThanSubmittedUserId() throws Exception {
        assertThrows(CommonException.class, () -> markdown.editTextByPath(attack()));
        assertEquals("PRIVATE ORIGINAL", Files.readString(victim));
        verifyNoInteractions(logs, events);
    }

    @Test
    void ownerCanEditAndVersionUsesResolvedOwnerId() throws Exception {
        when(login.getUsername()).thenReturn("alice");
        controller.editTextByPath(attack());
        assertEquals("OVERWRITTEN", Files.readString(victim));
        verify(events, timeout(2000)).publishEvent(argThat((FileVersionEvent version) ->
                "alice-id".equals(version.getUserId()) && "alice".equals(version.getFileUsername())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mountedEditRequiresBothAccessAndWritePermission(boolean hasAccess) throws Exception {
        UploadApiParamDTO upload = attack();
        upload.setMountFileId("share-file");
        FileDocument file = new FileDocument();
        file.setUserId("alice-id");
        file.setPath("/");
        file.setName("payroll.txt");
        // A readable share without PUT must not grant write access.
        file.setOperationPermissionList(hasAccess ? List.of(OperationPermission.DOWNLOAD) : List.of(OperationPermission.PUT));
        when(common.getById("share-file")).thenReturn(file);
        when(access.isNotAllowAccess(eq(file), any())).thenReturn(!hasAccess);
        assertThrows(CommonException.class, () -> controller.editTextByPath(upload));
        assertEquals("PRIVATE ORIGINAL", Files.readString(victim));
        verifyNoInteractions(oss, logs, events);
    }

    @Test
    void authorizedWritableShareUsesServerPathAndPermissions() throws Exception {
        UploadApiParamDTO upload = attack();
        upload.setMountFileId("share-file");
        upload.setRelativePath("/unrelated.txt");
        FileDocument file = new FileDocument();
        file.setUserId("alice-id");
        file.setPath("/");
        file.setName("payroll.txt");
        file.setOperationPermissionList(List.of(OperationPermission.PUT));
        when(common.getById("share-file")).thenReturn(file);
        controller.editTextByPath(upload);
        assertEquals("OVERWRITTEN", Files.readString(victim));
        assertFalse(Files.exists(root.resolve("alice/unrelated.txt")));
        verify(access).isNotAllowAccess(eq(file), any());
    }
}
