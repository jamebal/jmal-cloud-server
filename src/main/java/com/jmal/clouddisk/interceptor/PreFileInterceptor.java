package com.jmal.clouddisk.interceptor;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.WebConfig;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreFileInterceptor implements HandlerInterceptor {

    private final CommonFileService commonFileService;

    private final IUserService userService;

    private static final Cache<String, String> INTERNAL_TOKEN_CACHE = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();

    private static final int PATH_SEGMENTS_COUNT = 5;

    private static void setInternalTokenCache(String requestId, String token) {
        INTERNAL_TOKEN_CACHE.put(requestId, token);
    }

    public static boolean isValidInternalTokenCache(String requestId, String token) {
        if (CharSequenceUtil.isBlank(token)) {
            return false;
        }
        String cachedToken = INTERNAL_TOKEN_CACHE.getIfPresent(requestId);
        boolean isValid = token.equals(cachedToken);
        INTERNAL_TOKEN_CACHE.invalidate(requestId);
        return isValid;
    }


    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String uri = request.getRequestURI();
        String[] pathSegments = uri.split("/");

        // 验证路径格式
        if (pathSegments.length != PATH_SEGMENTS_COUNT || !"pre-file".equals(pathSegments[PATH_SEGMENTS_COUNT - 3])) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        String fileId = pathSegments[PATH_SEGMENTS_COUNT - 2];
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
        setInternal(request, fileDocument.getId());
        String internalFilePath = constructInternalFilePath(fileDocument);
        try {
            request.setAttribute("forward", true);
            request.getRequestDispatcher(internalFilePath).forward(request, response);
        } catch (Exception e) {
            handleForwardException(response, internalFilePath, e);
        }
    }

    private static void setInternal(HttpServletRequest request, String fileId) {
        // 生成临时 token
        String requestId = UUID.fastUUID().toString(true) + fileId;
        String internalToken = DigestUtil.sha256Hex(requestId + System.currentTimeMillis());
        request.setAttribute("requestId", requestId);
        request.setAttribute("internalToken", internalToken);
        setInternalTokenCache(requestId, internalToken);
    }

    public FileDocument getFileDocument(HttpServletResponse response, String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        FileDocument fileDocument = commonFileService.getById(fileId);
        if (fileDocument == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return fileDocument;
    }

    private String constructInternalFilePath(FileDocument fileDocument) {
        return WebConfig.API_FILE_PREFIX + userService.getUserNameById(fileDocument.getUserId()) + fileDocument.getPath() + fileDocument.getName();
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
