package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.interceptor.PreFileInterceptor;
import com.jmal.clouddisk.interceptor.ShareFileInterceptor;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserFileService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.CaffeineUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileControllerAccessTest {
    private final IFileService files = mock(IFileService.class);
    private final CommonFileService common = mock(CommonFileService.class);
    private final WebOssService oss = mock(WebOssService.class);
    private final UserLoginHolder login = mock(UserLoginHolder.class);
    private final IUserService users = mock(IUserService.class);
    private final IShareService shares = mock(IShareService.class);
    private FileController controller;
    private FileDocument document;

    @BeforeEach
    void setUp() {
        ShareFileInterceptor shareInterceptor = new ShareFileInterceptor(shares,
                mock(PreFileInterceptor.class), login);
        FileInterceptor access = new FileInterceptor(new FileProperties(), files, common, null, shareInterceptor, oss);
        controller = new FileController(files, common, mock(UserFileService.class), oss, login, users,
                access, new MockHttpServletRequest());
        document = new FileDocument();
        document.setId("victim-file");
        document.setUserId("bob");
        when(common.getById("victim-file")).thenReturn(document);
        when(login.getUserId()).thenReturn("alice");
        when(users.getAvatarByUsername("bob")).thenReturn("victim-file");
        when(files.thumbnail("victim-file", false)).thenReturn(Optional.empty());
        when(files.coverOfMedia("victim-file", null)).thenReturn(Optional.empty());
    }

    static Stream<Arguments> accessCases() {
        return Stream.of("info", "text", "stream", "thumbnail", "thumbnailName", "avatar", "cover")
                .flatMap(endpoint -> Stream.of("owner", "private", "public", "shared", "expired", "removed", "protected", "mounted", "missing")
                        .map(scenario -> Arguments.of(endpoint, scenario)));
    }

    @ParameterizedTest
    @MethodSource("accessCases")
    void enforcesFileAccessBeforeReading(String endpoint, String scenario) {
        boolean allowed = switch (scenario) {
            case "owner", "public", "shared", "mounted" -> true;
            default -> false;
        };
        switch (scenario) {
            case "owner" -> when(login.getUserId()).thenReturn("bob");
            case "public" -> document.setIsPublic(true);
            case "missing" -> when(common.getById("victim-file")).thenReturn(null);
            case "shared", "expired", "removed", "protected", "mounted" -> {
                document.setIsShare(true);
                document.setExpiresAt(scenario.equals("expired") ? 0L : Long.MAX_VALUE);
                document.setShareId("share-id");
                ShareDO share = new ShareDO();
                share.setId("share-id");
                share.setFileId("victim-file");
                share.setIsPrivacy(scenario.equals("protected") || scenario.equals("mounted"));
                when(shares.getShare("share-id")).thenReturn(scenario.equals("removed") ? null : share);
                when(shares.existsMountFile("victim-file", "alice")).thenReturn(scenario.equals("mounted"));
            }
        }
        if (allowed) {
            assertDoesNotThrow(() -> invoke(endpoint));
        } else {
            CommonException exception = assertThrows(CommonException.class, () -> invoke(endpoint));
            assertEquals((scenario.equals("missing") ? ExceptionType.FILE_NOT_FIND : ExceptionType.ACCESS_FORBIDDEN).getCode(), exception.getCode());
            verifyNoInteractions(files, oss);
        }
    }

    private void invoke(String endpoint) {
        switch (endpoint) {
            case "info" -> controller.getFileById("victim-file");
            case "text" -> controller.previewText("victim-file", "/", "ignored.txt", true);
            case "stream" -> controller.previewTextStream("victim-file", "/", "ignored.txt");
            case "thumbnail" -> controller.thumbnail("victim-file", false);
            case "thumbnailName" -> controller.thumbnailName("victim-file", false);
            case "avatar" -> controller.thumbnailByUsername("bob", false);
            case "cover" -> controller.coverOfMedia("victim-file");
            default -> throw new AssertionError(endpoint);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ossPreviewReadsAuthorizedIdInsteadOfClientSuppliedPath(boolean stream) {
        String prefix = "/bob/access-test-bucket";
        String id = prefix + "/allowed.txt";
        CaffeineUtil.setOssDiameterPrefixCache(prefix, mock(BucketInfo.class));
        when(common.getById(id)).thenReturn(document);
        when(login.getUserId()).thenReturn("bob");
        try {
            if (stream) {
                controller.previewTextStream(id, "/access-test-bucket/private", "secret.txt");
                verify(oss).readToTextStream(prefix, Paths.get(id));
            } else {
                controller.previewText(id, "/access-test-bucket/private", "secret.txt", true);
                verify(oss).readToText(prefix, Paths.get(id), true);
            }
            verifyNoMoreInteractions(oss);
        } finally {
            CaffeineUtil.removeOssDiameterPrefixCache(prefix);
        }
    }
}
