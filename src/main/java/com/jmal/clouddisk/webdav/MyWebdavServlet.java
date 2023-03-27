package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.webdav.resource.AliyunOSSFileResource;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.WebResource;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.tomcat.util.http.parser.Ranges;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    @Override
    protected void copy(WebResource resource, long length, ServletOutputStream ostream, Ranges.Entry range) throws IOException {
        IOException exception = null;

        InputStream resourceInputStream = resource.getInputStream();
        InputStream istream = new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(istream, ostream, getStart(range, length), getEnd(range, length));
        Console.log(resource.getName(), exception);
        // Clean up the input stream
        istream.close();

        if (resource instanceof AliyunOSSFileResource aliyunOSSFileResource) {
            aliyunOSSFileResource.closeObject();
        }

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }
    }

    private static long getStart(Ranges.Entry range, long length) {
        long start = range.getStart();
        if (start == -1 ) {
            long end = range.getEnd();
            // If there is no start, then the start is based on the end
            if (end >= length) {
                return 0;
            } else {
                return length - end;
            }
        } else {
            return start;
        }
    }

    private static long getEnd(Ranges.Entry range, long length) {
        long end = range.getEnd();
        if (range.getStart() == -1 || end == -1 || end >= length) {
            return length - 1;
        } else {
            return end;
        }
    }


}
