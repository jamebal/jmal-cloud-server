package com.jmal.clouddisk.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author jmal
 * @Description
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareFileInterceptor implements HandlerInterceptor {

    private final IShareService shareService;

    private final PreFileInterceptor preFileInterceptor;

    private final UserLoginHolder userLoginHolder;

    private static final int PATH_SEGMENTS_COUNT = 5;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String uri = request.getRequestURI();
        String[] pathSegments = uri.split("/");

        // 验证路径格式
        if (pathSegments.length < PATH_SEGMENTS_COUNT || !"share-file".equals(pathSegments[PATH_SEGMENTS_COUNT - 3])) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        String shareToken = null;
        if (pathSegments.length == (PATH_SEGMENTS_COUNT + 1)) {
            shareToken = pathSegments[PATH_SEGMENTS_COUNT - 1];
        }

        String fileId = pathSegments[PATH_SEGMENTS_COUNT - 2];
        FileDocument fileDocument = preFileInterceptor.getFileDocument(response, fileId);
        if (fileDocument == null) {
            return false;
        }

        // 验证权限
        if (isNotAllowAccess(fileDocument, shareToken, request)) {
            return false;
        }

        // 构造内部路径
        preFileInterceptor.internalFilePath(request, response, fileDocument);

        // 阻止原始请求继续处理
        return false;
    }

    private boolean isNotAllowAccess(FileDocument fileDocument, String shareToken, HttpServletRequest request) {
        if (BooleanUtil.isTrue(fileDocument.getIsShare()) || BooleanUtil.isTrue(fileDocument.getIsPublic())) {
            return validShareFile(fileDocument, shareToken, request);
        }
        return true;
    }

    public boolean validShareFile(FileDocument fileDocument, String shareToken, HttpServletRequest request) {
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
        if (BooleanUtil.isFalse(shareDO.getIsPrivacy())) {
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

}
