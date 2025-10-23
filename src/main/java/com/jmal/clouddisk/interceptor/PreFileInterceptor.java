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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreFileInterceptor implements HandlerInterceptor {

    private final CommonFileService commonFileService;
    private final IUserService userService;

    private static final Cache<String, String> INTERNAL_TOKEN_CACHE =
            Caffeine.newBuilder()
                    .expireAfterWrite(5, TimeUnit.SECONDS)
                    .build();

    private static final String PRE_FILE_PREFIX = "/api/pre-file/";

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
    public boolean preHandle(@NotNull HttpServletRequest request,
                             @NotNull HttpServletResponse response,
                             @NotNull Object handler) {
        String uri = request.getRequestURI();

        if (!uri.startsWith(PRE_FILE_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }

        // 提取 prefix 后的完整路径
        String remainingPath = uri.substring(PRE_FILE_PREFIX.length());

        // 解析路径
        ParsedPath parsedPath = parsePath(remainingPath);

        if (parsedPath == null || CharSequenceUtil.isBlank(parsedPath.fileId)) {
            log.warn("Invalid path format: {}", uri);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }

        log.debug("Parsed - fileId: {}, filename: {}", parsedPath.fileId, parsedPath.filename);

        FileDocument fileDocument = getFileDocument(response, parsedPath.fileId);
        if (fileDocument == null) {
            return false;
        }

        // 将解析后的参数设置到 request attribute 中
        request.setAttribute("fileId", parsedPath.fileId);
        request.setAttribute("filename", parsedPath.filename);

        // 构造内部路径
        internalFilePath(request, response, fileDocument);

        // 阻止原始请求继续处理
        return false;
    }

    /**
     * 解析路径，格式: /api/pre-file/{fileId}/{filename}
     * fileId 可以包含多级路径，如: test/rustfs/新建文件夹
     */
    private ParsedPath parsePath(String path) {
        if (CharSequenceUtil.isBlank(path)) {
            return null;
        }

        // URL 解码处理中文等特殊字符
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);

        // 移除首尾斜杠
        path = path.trim();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (CharSequenceUtil.isBlank(path)) {
            return null;
        }

        // 从后往前查找最后一个斜杠
        int lastSlashIndex = path.lastIndexOf('/');

        ParsedPath result = new ParsedPath();

        if (lastSlashIndex < 0) {
            // 没有斜杠，说明格式错误（至少需要 fileId/filename）
            return null;
        }

        // fileId 是最后一个斜杠之前的部分
        result.fileId = path.substring(0, lastSlashIndex);

        // filename 是最后一个斜杠之后的部分
        result.filename = path.substring(lastSlashIndex + 1);

        // 验证 fileId 和 filename 不能为空
        if (CharSequenceUtil.isBlank(result.fileId) || CharSequenceUtil.isBlank(result.filename)) {
            return null;
        }

        return result;
    }

    public void internalFilePath(HttpServletRequest request, HttpServletResponse response,
                                 FileDocument fileDocument) {
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
        return WebConfig.API_FILE_PREFIX +
                userService.getUserNameById(fileDocument.getUserId()) +
                fileDocument.getPath() +
                fileDocument.getName();
    }

    private static void handleForwardException(HttpServletResponse response, String internalFilePath,
                                               Exception e) {
        log.error("Failed to forward request to {}", internalFilePath, e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        try {
            response.getWriter().write("Internal server error");
        } catch (IOException ioException) {
            log.error("Failed to write response", ioException);
        }
    }

    /**
     * 路径解析结果
     */
    @Data
    private static class ParsedPath {
        private String fileId;
        private String filename;
    }
}
