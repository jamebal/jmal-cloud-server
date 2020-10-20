package com.jmal.clouddisk.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.util.CaffeineUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author jmal
 * @Description 鉴权拦截器
 * @Date 2020-01-31 22:04
 */
@Slf4j
@Component
public class FileInterceptor implements HandlerInterceptor {

    public static final String FILE_KEY = "fileKey";

    private static final String DOWNLOAD = "download";

    private static final long ONE_WEEK = 7 * 1000L * 60 * 60 * 24;

    private final Cache<String, String> tokenCache = CaffeineUtil.getTokenCache();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String operation = request.getParameter("o");
        if(DOWNLOAD.equals(operation)){
            Path path = Paths.get(request.getRequestURI());
            response.setHeader("Content-Disposition", "attachment; filename"+ path.getFileName());
        }
        return true;
    }

}
