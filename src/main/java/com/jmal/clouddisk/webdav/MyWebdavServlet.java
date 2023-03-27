package com.jmal.clouddisk.webdav;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.servlets.WebdavServlet;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author jmal
 * @Description WebdavServlet
 * @date 2023/3/27 09:35
 */
@Component
public class MyWebdavServlet extends WebdavServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        if (method.equals(WebdavMethod.PROPFIND.getCode())) {
            Path path = Paths.get(request.getRequestURI());
            if (path.getFileName().toString().startsWith("._")) {
                response.sendError(404);
                return;
            }
        }
        super.service(request, response);
    }

}
