package com.jmal.clouddisk.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShareFileInterceptor implements HandlerInterceptor {

    private final IShareService shareService;
    private final PreFileInterceptor preFileInterceptor;
    private final UserLoginHolder userLoginHolder;

    private static final String SHARE_FILE_PREFIX = "/api/share-file/";

    /**
     * shareToken 格式识别正则
     * 格式: {shortKey}:{relativeExpiry}.{base64Signature}
     * - shortKey: 字母数字组合（无横线的短ID）
     * - relativeExpiry: 纯数字（相对时间戳）
     * - signature: Base64 URL-safe 编码（无填充）
     */
    private static final Pattern SHARE_TOKEN_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]+:[0-9]+\\.[A-Za-z0-9_\\-]+$");

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request,
                             @NotNull HttpServletResponse response,
                             @NotNull Object handler) {
        String uri = request.getRequestURI();

        if (!uri.startsWith(SHARE_FILE_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }

        // 提取 prefix 后的完整路径
        String remainingPath = uri.substring(SHARE_FILE_PREFIX.length());

        // 解析路径
        ParsedPath parsedPath = parsePath(remainingPath);

        if (parsedPath == null || CharSequenceUtil.isBlank(parsedPath.fileId)) {
            log.warn("Invalid path format: {}", uri);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }

        log.debug("Parsed - fileId: {}, shareToken: {}, filename: {}",
                parsedPath.fileId, parsedPath.shareToken, parsedPath.filename);

        FileDocument fileDocument = preFileInterceptor.getFileDocument(response, parsedPath.fileId);
        if (fileDocument == null) {
            return false;
        }

        // 验证权限
        if (isNotAllowAccess(fileDocument, parsedPath.shareToken, request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        // 将解析后的参数设置到 request attribute 中，供后续使用
        request.setAttribute("fileId", parsedPath.fileId);
        request.setAttribute("shareToken", parsedPath.shareToken);
        request.setAttribute("filename", parsedPath.filename);

        // 构造内部路径
        preFileInterceptor.internalFilePath(request, response, fileDocument);

        // 阻止原始请求继续处理
        return false;
    }

    /**
     * 解析路径，支持两种格式：
     * 1. /api/share-file/{fileId}/{shareToken}/{filename}
     * 2. /api/share-file/{fileId}/{filename}
     * 策略：从后往前扫描，找到符合 shareToken 格式的段
     */
    private ParsedPath parsePath(String path) {
        if (CharSequenceUtil.isBlank(path)) {
            return null;
        }

        // URL 解码处理中文等特殊字符
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);

        String[] segments = path.split("/");
        if (segments.length < 2) {
            return null;
        }

        ParsedPath result = new ParsedPath();

        // 最后一段必定是 filename
        result.filename = segments[segments.length - 1];

        // 从倒数第二段开始，向前查找 shareToken
        int shareTokenIndex = -1;
        for (int i = segments.length - 2; i >= 0; i--) {
            if (isShareToken(segments[i])) {
                shareTokenIndex = i;
                break;
            }
        }

        if (shareTokenIndex > 0) {
            // 格式1: 带 shareToken
            result.shareToken = segments[shareTokenIndex];

            // fileId 是 shareToken 之前的所有部分
            result.fileId = String.join("/",
                    Arrays.copyOfRange(segments, 0, shareTokenIndex));
        } else {
            // 格式2: 不带 shareToken
            result.shareToken = null;

            // fileId 是除了最后一段之外的所有部分
            result.fileId = String.join("/",
                    Arrays.copyOfRange(segments, 0, segments.length - 1));
        }

        // 移除 fileId 首尾的斜杠
        result.fileId = result.fileId.trim();
        if (result.fileId.startsWith("/")) {
            result.fileId = result.fileId.substring(1);
        }
        if (result.fileId.endsWith("/")) {
            result.fileId = result.fileId.substring(0, result.fileId.length() - 1);
        }

        return result;
    }

    /**
     * 判断字符串是否为 shareToken
     * shareToken 格式: {shortKey}:{relativeExpiry}.{signature}
     * 示例: 68f74bf842d14653658774c9:424125.U01uREMTZ4lNFEDjVffvIs8Fki04F0x_PafexIp-_7A
     * 组成部分：
     * - shortKey: 字母数字组合（压缩后的 key，无横线）
     * - relativeExpiry: 纯数字（相对于 2025-01-01 的分钟数）
     * - signature: Base64 URL-safe 编码（字母、数字、下划线、横线）
     */
    private boolean isShareToken(String segment) {
        if (CharSequenceUtil.isBlank(segment)) {
            return false;
        }

        // 基本格式检查：必须包含 : 和 .
        if (!segment.contains(":") || !segment.contains(".")) {
            return false;
        }

        // 正则匹配
        if (!SHARE_TOKEN_PATTERN.matcher(segment).matches()) {
            return false;
        }

        // 额外验证：确保数字部分（relativeExpiry）合理
        try {
            String[] parts = segment.split("\\.");
            if (parts.length != 2) {
                return false;
            }

            String[] dataParts = parts[0].split(":");
            if (dataParts.length != 2) {
                return false;
            }

            int relativeExpiry = Integer.parseInt(dataParts[1]);

            // relativeExpiry 应该是正数且在合理范围内
            // 假设 token 最长有效期 10 年 = 10 * 365 * 24 * 60 = 5256000 分钟
            return relativeExpiry >= 0 && relativeExpiry <= 10000000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isNotAllowAccess(FileDocument fileDocument, String shareToken,
                                     HttpServletRequest request) {
        if (BooleanUtil.isTrue(fileDocument.getIsShare()) ||
                BooleanUtil.isTrue(fileDocument.getIsPublic())) {
            return validShareFile(fileDocument, shareToken, request);
        }
        return true;
    }

    public boolean validShareFile(FileDocument fileDocument, String shareToken,
                                  HttpServletRequest request) {
        if (BooleanUtil.isTrue(fileDocument.getIsPublic())) {
            return false;
        }
        if (System.currentTimeMillis() >= fileDocument.getExpiresAt()) {
            // 过期了
            return true;
        }
        if (fileDocument.getShareId() == null) {
            // 未分享
            return true;
        }
        ShareDO shareDO = shareService.getShare(fileDocument.getShareId());
        if (shareDO == null) {
            // 分享不存在
            return true;
        }
        if (!BooleanUtil.isTrue(shareDO.getIsPrivacy())) {
            return false;
        }
        if (request == null) {
            // 未登录
            return true;
        }
        if (CharSequenceUtil.isBlank(shareToken)) {
            // 判断是否为挂载文件
            String userId = userLoginHolder.getUserId();
            if (CharSequenceUtil.isNotBlank(userId)) {
                return !shareService.existsMountFile(shareDO.getFileId(), userId);
            }
            // 从 header 或 parameter 中获取 shareToken
            shareToken = request.getHeader(Constants.SHARE_TOKEN);
            if (CharSequenceUtil.isBlank(shareToken)) {
                shareToken = request.getParameter(Constants.SHARE_TOKEN);
            }
        }
        if (CharSequenceUtil.isBlank(shareToken)) {
            // 未携带share-token
            return true;
        }
        shareService.validShare(shareToken, shareDO.getId());
        return false;
    }

    /**
     * 路径解析结果
     */
    @Data
    private static class ParsedPath {
        private String fileId;
        private String shareToken;
        private String filename;
    }
}
