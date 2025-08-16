package com.jmal.clouddisk.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.DirectLinkService;
import com.jmal.clouddisk.service.impl.FileServiceImpl;
import com.jmal.clouddisk.service.impl.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * @author jmal
 * @Description
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectFileInterceptor implements HandlerInterceptor {

    private final DirectLinkService directLinkService;

    private final PreFileInterceptor preFileInterceptor;

    private final FileServiceImpl fileService;

    private final IUserService userService;

    private final RoleService roleService;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String uri = request.getRequestURI();
        String[] pathSegments = uri.split("/");

        // 验证路径格式
        if (pathSegments.length != 4 || !"direct-file".equals(pathSegments[1])) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        String mark = pathSegments[2];
        String fileId = directLinkService.getFileIdByMark(mark);
        if (CharSequenceUtil.isBlank(fileId)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        FileDocument fileDocument = preFileInterceptor.getFileDocument(response, fileId);
        if (fileDocument == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        if (BooleanUtil.isTrue(fileDocument.getIsFolder())) {
            // 打包下载
            String username = userService.getUserNameById(fileDocument.getUserId());
            List<String> authorities = roleService.getAuthorities(username);
            if (!authorities.contains("cloud:file:packageDownload")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
            fileService.packageDownload(request, response, List.of(fileId), username);
            return false;
        }
        // 构造内部路径
        preFileInterceptor.internalFilePath(request, response, fileDocument);
        // 阻止原始请求继续处理
        return false;
    }

}
