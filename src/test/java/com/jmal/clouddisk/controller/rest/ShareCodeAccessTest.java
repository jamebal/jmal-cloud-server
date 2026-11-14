package com.jmal.clouddisk.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.service.impl.ShareServiceImpl;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShareCodeAccessTest {
    private final IShareDAO dao = mock(IShareDAO.class);
    private final CommonUserService users = mock(CommonUserService.class);
    private final UserLoginHolder login = mock(UserLoginHolder.class);
    private final ShareServiceImpl service = new ShareServiceImpl(null, dao, null, null,
            mock(IWebsiteSettingDAO.class), users, null, login, null);
    private final ShareController controller = new ShareController(service, null, null, null, null, null, login);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ShareDO share;

    @BeforeEach
    void setUp() {
        share = new ShareDO();
        share.setId("507f1f77bcf86cd799439011");
        share.setShortId("test-link");
        share.setFileId("file-id");
        share.setUserId("owner");
        share.setIsPrivacy(true);
        share.setExtractionCode("abcd");
        when(dao.findById(share.getId())).thenReturn(share);
        when(dao.findByShortId(share.getShortId())).thenReturn(share);
        when(dao.findByFileId(share.getFileId())).thenReturn(share);
        ConsumerDO owner = new ConsumerDO();
        owner.setUsername("owner");
        owner.setShowName("Owner");
        when(users.getUserInfoById("owner")).thenReturn(owner);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"other-user", ""})
    void nonOwnersCannotReadCodeViaEitherIdentifier(String caller) {
        when(login.getUserId()).thenReturn(caller);
        assertEquals(ExceptionType.PERMISSION_DENIED.getCode(),
                assertThrows(CommonException.class, () -> controller.getShare(share.getId())).getCode());
        assertThrows(CommonException.class, () -> controller.getShare(share.getShortId()));
        assertThrows(CommonException.class, () -> controller.getShareByFileId(share.getFileId()));
    }

    @Test
    void ownerCanStillRetrieveExtractionCode() throws Exception {
        when(login.getUserId()).thenReturn("owner");
        assertEquals("abcd", mapper.valueToTree(controller.getShare(share.getId())).at("/data/extractionCode").asText());
        assertSame(share, controller.getShareByFileId(share.getFileId()).getData());
    }

    @Test
    void publicSharerDtoDoesNotExposeCodeAndValidCodeExchangeStillWorks() throws Exception {
        String publicJson = mapper.writeValueAsString(controller.getSharer("test-link"));
        assertFalse(publicJson.contains("extractionCode"));
        assertFalse(publicJson.contains("abcd"));
        assertEquals(share.getId(), controller.getSharer("test-link").getData().getShareId());
        assertNull(controller.validShareCode(Map.of("shareId", share.getId(), "shareCode", "wrong")).getData());
        Object token = controller.validShareCode(Map.of("shareId", share.getId(), "shareCode", "abcd")).getData();
        assertNotNull(token);
        assertDoesNotThrow(() -> service.validShare(token.toString(), share.getId()));
    }

    @Test
    void listIgnoresForgedOwnerAndScopesDaoQueryToCaller() {
        when(login.getUserId()).thenReturn("other-user");
        UploadApiParamDTO upload = new UploadApiParamDTO();
        upload.setUserId("owner");
        when(dao.findShareList(upload)).thenAnswer(invocation -> {
            assertEquals("other-user", upload.getUserId());
            return Page.empty();
        });
        controller.shareList(upload);
        verify(dao).findShareList(upload);
    }

    @Test
    void listWithoutCallerFailsBeforeQueryingDao() {
        assertThrows(CommonException.class, () -> controller.shareList(new UploadApiParamDTO()));
        verify(dao, never()).findShareList(any());
    }
}
