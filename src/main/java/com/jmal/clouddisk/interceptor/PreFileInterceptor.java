package com.jmal.clouddisk.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * @author jmal
 * @Description
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreFileInterceptor implements HandlerInterceptor {

    private final IFileService fileService;

    private final IUserService userService;


    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String uri = request.getRequestURI();
        String[] pathSegments = uri.split("/");

        // 验证路径格式
        if (pathSegments.length != 4 || !"pre-file".equals(pathSegments[1])) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        String fileId = pathSegments[2];
        FileDocument fileDocument = getFileDocument(response, fileId);
        if (fileDocument == null) {
            return false;
        }

        // 构造内部路径
        internalFilePath(request, response, fileDocument);

        // 阻止原始请求继续处理
        return false;
    }

    public void internalFilePath(HttpServletRequest request, HttpServletResponse response, FileDocument fileDocument) {
        String internalFilePath = constructInternalFilePath(fileDocument);
        try {
            request.setAttribute("forward", true);
            request.getRequestDispatcher(internalFilePath).forward(request, response);
        } catch (Exception e) {
            handleForwardException(response, internalFilePath, e);
        }
    }

    public FileDocument getFileDocument(HttpServletResponse response, String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return fileDocument;
    }

    private String constructInternalFilePath(FileDocument fileDocument) {
        return "/file/" + userService.getUserNameById(fileDocument.getUserId()) + fileDocument.getPath() + fileDocument.getName();
    }

    private static void handleForwardException(HttpServletResponse response, String internalFilePath, Exception e) {
        log.error("Failed to forward request to {}", internalFilePath, e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        try {
            response.getWriter().write("Internal server error");
        } catch (IOException ioException) {
            log.error("Failed to write response", ioException);
        }
    }


}
