package com.jmal.clouddisk.interceptor;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileAccessTest {
    private final CommonFileService common = mock(CommonFileService.class);
    private final IUserService users = mock(IUserService.class);
    private final IFileService files = mock(IFileService.class);
    private final AuthInterceptor auth = mock(AuthInterceptor.class);
    private final ShareFileInterceptor shares = mock(ShareFileInterceptor.class);
    private final WebOssService oss = mock(WebOssService.class);
    private final PreFileInterceptor pre = new PreFileInterceptor(common, users);
    private FileInterceptor interceptor;
    private FileDocument document;

    @BeforeEach
    void setUp() {
        FileProperties properties = new FileProperties();
        properties.setRootDir(System.getProperty("java.io.tmpdir"));
        interceptor = new FileInterceptor(properties, files, common, auth, shares, oss);
        document = new FileDocument();
        document.setId("file-id");
        document.setUserId("bob-id");
        document.setPath("/photos/");
        document.setName("secret.jpg");
        when(common.getById("file-id")).thenReturn(document);
        when(users.getUserNameById("bob-id")).thenReturn("bob");
        when(files.getFileDocumentByPathAndName("/photos/", "secret.jpg", "bob", true))
                .thenReturn(document);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("User-Agent", "test");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, uri);
        return request;
    }

    private MockHttpServletRequest preview(MockHttpServletResponse response) {
        MockHttpServletRequest request = request("/api/pre-file/file-id/anything.jpg");
        assertFalse(pre.preHandle(request, response, new Object()));
        assertNull(request.getAttribute("internalToken"));
        assertEquals("/api/file/bob/photos/secret.jpg", response.getForwardedUrl());
        // MockRequestDispatcher records the forward; emulate its target dispatch.
        request.setRequestURI(response.getForwardedUrl());
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, response.getForwardedUrl());
        request.setParameter("o", "download");
        return request;
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"alice"})
    void privatePreviewRejectsAnonymousAndOtherUsers(String username) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = preview(response);
        when(auth.getUserNameByHeader(request, response)).thenReturn(username);
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(oss);
    }

    @Test
    void ownerCanPreview() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = preview(response);
        when(auth.getUserNameByHeader(request, response)).thenReturn("bob");
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void publicPreviewRemainsAccessible() throws Exception {
        document.setIsPublic(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(preview(response), response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void sharedPreviewUsesExistingShareValidation(boolean denied) throws Exception {
        document.setIsShare(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = preview(response);
        when(shares.validShareFile(document, null, request)).thenReturn(denied);
        assertEquals(!denied, interceptor.preHandle(request, response, new Object()));
        assertEquals(denied ? 403 : 200, response.getStatus());
        verify(shares).validShareFile(document, null, request);
    }

    @ParameterizedTest
    @CsvSource({
            "alice-id, /, secret.jpg, false, bob-id, /, secret.jpg, false",
            "bob-id, /, shared.jpg, false, bob-id, /, secret.jpg, false",
            "bob-id, /, secret.jpg, false, bob-id, /, secret.jpg, true",
            "bob-id, /, photos, true, alice-id, /photos/, secret.jpg, false",
            "bob-id, /, photos, true, bob-id, /, secret.jpg, false",
            "bob-id, /, photos, true, bob-id, /photos-private/, secret.jpg, false",
            "bob-id, /, photos, true, bob-id, /photos/, secret.jpg, true",
            "bob-id, /, photos, true, bob-id, /photos/trip/, secret.jpg, true",
            "bob-id, /, photos, true, bob-id, /, photos, true",
            "bob-id, /, photos, false, bob-id, /photos/, secret.jpg, false"
    })
    void shareKeyOnlyGrantsAccessToItsOwnerAndScope(String sharedOwner, String sharedPath,
            String sharedName, boolean folder, String targetOwner, String targetPath,
            String targetName, boolean allowed) throws Exception {
        document.setUserId(sharedOwner);
        document.setPath(sharedPath);
        document.setName(sharedName);
        document.setIsFolder(folder);
        document.setIsShare(true);
        FileDocument target = new FileDocument();
        target.setUserId(targetOwner);
        target.setPath(targetPath);
        target.setName(targetName);
        when(files.getFileDocumentByPathAndName(targetPath, targetName, "target", true)).thenReturn(target);
        MockHttpServletRequest request = request("/api/file/target" + targetPath + targetName);
        request.setParameter("shareKey", "file-id");
        request.setParameter("o", "download");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(allowed, interceptor.preHandle(request, response, new Object()));
        assertEquals(allowed ? 200 : 403, response.getStatus());
        verify(shares).validShareFile(document, null, request);
        verifyNoInteractions(auth, oss);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalidShareKeyCannotGrantAccess(boolean missing) throws Exception {
        if (missing) {
            when(common.getById("file-id")).thenReturn(null);
        } else {
            document.setIsShare(true);
            when(shares.validShareFile(eq(document), isNull(), any())).thenReturn(true);
        }
        MockHttpServletRequest request = request("/api/file/bob/photos/secret.jpg");
        request.setParameter("shareKey", "file-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        verifyNoInteractions(files, oss);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/file/alice/x/../../bob/secret.jpg",
            "/api/file/alice/%2e%2e/bob/secret.jpg",
            "/api/file/alice/..%2fbob/secret.jpg",
            "/api/file/alice/%5c..%5cbob/secret.jpg",
            "/api/file/alice/..",
            "/api/file/alice/a%00.jpg"
    })
    void rejectsTraversalBeforeAuthorizationOrReading(String uri) throws Exception {
        for (String operation : new String[]{"crop", "webp", "thumbnail", "preview", "download"}) {
            MockHttpServletRequest request = request(uri);
            request.setParameter("o", operation);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(400, response.getStatus());
        }
        verifyNoInteractions(auth, shares, oss);
        verify(files, never()).getFileDocumentByPathAndName(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void trustedShareAndDirectForwardStillUseSingleUseToken() throws Exception {
        MockHttpServletRequest request = request("/api/share-file/file-id/secret.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();
        pre.internalFilePath(request, response, document);
        String requestId = (String) request.getAttribute("requestId");
        String token = (String) request.getAttribute("internalToken");
        assertNotNull(token);
        assertTrue(PreFileInterceptor.isValidInternalTokenCache(requestId, token));
        assertFalse(PreFileInterceptor.isValidInternalTokenCache(requestId, token));
    }

    @Test
    void internalForwardCannotBypassPathValidation() throws Exception {
        document.setPath("/../alice/");
        MockHttpServletRequest request = request("/api/direct-file/mark/secret.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();
        pre.internalFilePath(request, response, document);
        request.setRequestURI(response.getForwardedUrl());
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(400, response.getStatus());
    }
}
