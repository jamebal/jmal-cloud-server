package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IFileQueryDAO;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.IFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilePublicationAccessTest {
    @Mock private UserLoginHolder login;
    @Mock private CommonFileService common;
    @Mock private IFileService fileService;
    @Mock private IShareDAO shares;
    @Mock private IFileDAO files;
    @Mock private IFileQueryDAO query;
    @InjectMocks private ShareServiceImpl shareService;
    @InjectMocks private UserFileService userFileService;

    @Test
    void nonOwnerCannotCreateOrChangeAShareEvenWithForgedOwner() {
        when(login.getUserId()).thenReturn("bob-id");
        FileDocument file = aliceFile();
        when(common.getById(file.getId())).thenReturn(file);
        ShareDO request = new ShareDO();
        request.setFileId(file.getId());
        request.setUserId("alice-id");

        assertEquals(ExceptionType.PERMISSION_DENIED.getCode(),
                assertThrows(CommonException.class, () -> shareService.generateLink(request)).getCode());
        verifyNoInteractions(shares, fileService, files);
    }

    @Test
    void shareCreationUsesSessionOwnerAndDoesNotAcceptAnExistingRecordId() {
        when(login.getUserId()).thenReturn("alice-id");
        FileDocument file = aliceFile();
        when(common.getById(file.getId())).thenReturn(file);
        ShareDO request = new ShareDO();
        request.setFileId(file.getId());
        request.setUserId("bob-id");
        request.setId("another-record");
        request.setFatherShareId("another-parent");
        when(shares.save(request)).thenAnswer(invocation -> {
            assertEquals("alice-id", request.getUserId());
            assertNull(request.getId());
            assertNull(request.getFatherShareId());
            request.setId("new-share");
            return request;
        });

        assertEquals(0, shareService.generateLink(request).getCode());
        verify(fileService).setShareFile(file, Long.MAX_VALUE, request);
    }

    @Test
    void onlyOwnerCanSetThePublicFlag() {
        FileDocument file = aliceFile();
        when(query.findBaseFileDocumentById(file.getId(), true)).thenReturn(file);
        when(login.getUserId()).thenReturn("bob-id");
        assertEquals(ExceptionType.PERMISSION_DENIED.getCode(),
                assertThrows(CommonException.class, () -> userFileService.setPublic(file.getId())).getCode());
        verifyNoInteractions(files);

        when(login.getUserId()).thenReturn("alice-id");
        userFileService.setPublic(file.getId());
        verify(files).updateIsPublicById(file.getId());
    }

    private static FileDocument aliceFile() {
        FileDocument file = new FileDocument();
        file.setId("alice-file");
        file.setUserId("alice-id");
        return file;
    }
}
