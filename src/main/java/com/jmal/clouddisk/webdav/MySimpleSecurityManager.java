package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.http11.auth.DigestGenerator;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description webDAV 认证授权
 *
 * HTTP 1.1（请参阅 IETF RFC 2068）提供一组可供客户端与服务器通讯的方法，并指定响应（从服务器返回发出请求的客户端）的格式。 WebDAV 完全采用此规范中的所有方法，扩展其中的一些方法，并引入了其他可提供所描述功能的方法。 WebDAV 中使用的方法包括：
 * 1.Options、Head 和 Trace。
 * 主要由应用程序用来发现和跟踪服务器支持和网络行为。
 * 2.Get。
 * 检索文档。
 * 3.Put 和 Post。
 * 将文档提交到服务器。
 * 4.Delete。
 * 销毁资源或集合。
 * 5. Mkcol。
 * 创建集合。
 * 6.PropFind 和 PropPatch。
 * 针对资源和集合检索和设置属性。
 * 7.Copy 和 Move。
 * 管理命名空间上下文中的集合和资源。
 * 8. Lock 和 Unlock。
 * 改写保护。
 *
 * @Date 2021/1/29 2:03 下午
 */
@Component
@Slf4j
public class MySimpleSecurityManager implements io.milton.http.SecurityManager {

    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private FileProperties fileProperties;
    private String realm;
    private DigestGenerator digestGenerator;

    private static final List<Request.Method> DELETES_METHODS = Arrays.asList(Request.Method.GET,Request.Method.HEAD,Request.Method.OPTIONS,Request.Method.PROPFIND,Request.Method.MKCOL,Request.Method.PUT,Request.Method.PUT, Request.Method.MOVE, Request.Method.COPY, Request.Method.PROPPATCH,Request.Method.DELETE);
    private static final List<Request.Method> UPDATE_METHODS = Arrays.asList(Request.Method.GET,Request.Method.HEAD,Request.Method.OPTIONS,Request.Method.PROPFIND,Request.Method.MKCOL,Request.Method.PUT,Request.Method.PUT, Request.Method.MOVE, Request.Method.COPY, Request.Method.PROPPATCH);
    private static final List<Request.Method> UPLOAD_METHODS = Arrays.asList(Request.Method.GET,Request.Method.HEAD,Request.Method.OPTIONS,Request.Method.PROPFIND,Request.Method.MKCOL,Request.Method.PUT);
    private static final List<Request.Method> LIST_METHODS = Arrays.asList(Request.Method.GET,Request.Method.HEAD,Request.Method.OPTIONS,Request.Method.PROPFIND,Request.Method.MKCOL);

    public MySimpleSecurityManager() {
        realm = "userRealm";
        digestGenerator = new DigestGenerator();
    }

    public MySimpleSecurityManager(DigestGenerator digestGenerator) {
        this.digestGenerator = digestGenerator;
    }

    public MySimpleSecurityManager(String realm) {
        this.realm = realm;
        digestGenerator = new DigestGenerator();
    }

    @Override
    public Object authenticate(String user, String password) {
        log.debug("authenticate: " + user + " - " + password);
        // user name will include domain when coming form ftp. we just strip it off
        if (user.contains("@")) {
            user = user.substring(0, user.indexOf("@"));
        }
        String actualPassword = userService.getPasswordByUserName(user);
        if (actualPassword == null) {
            log.debug("user not found: " + user);
            return null;
        } else {
            return (actualPassword.equals(password)) ? user : null;
        }
    }

    @Override
    public Object authenticate(DigestResponse digestRequest) {
        if (digestGenerator == null) {
            throw new RuntimeException("No digest generator is configured");
        }
        String username = digestRequest.getUser();
        String uri = digestRequest.getUri().replaceFirst(fileProperties.getWebDavPrefixPath(), "");
        Console.log(uri);
        Path path = Paths.get(uri);
        if (path.getNameCount() < 1) {
            return null;
        }
        if (!username.equals(path.getName(0).toString())) {
            return null;
        }
        String actualPassword = userService.getPasswordByUserName(username);
        String serverResponse = digestGenerator.generateDigest(digestRequest, actualPassword);
        String clientResponse = digestRequest.getResponseDigest();
        if (serverResponse.equals(clientResponse)) {
            return "ok";
        } else {
            return null;
        }
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth, Resource resource) {
        // switch(method) {
        //     case GET: return true;
        //     case HEAD: return true;
        //     case OPTIONS: return true;
        //     case PROPFIND: return true;
        // }
        if (auth != null) {
            List<Request.Method> methods = allowMethods(auth.getUser());
            if (methods.contains(method)) {
                return auth.getTag() != null;
            }
        }
        return false;
    }

    /***
     * 根据该用户拥有的权限获取允许访问的方法列表
     * @return 允许访问的方法列表
     */
    public List<Request.Method> allowMethods(String username){
        int maxAuthority = maxAuthority(username);
        switch (maxAuthority) {
            case 3:
                return DELETES_METHODS;
            case 2:
                return UPDATE_METHODS;
            case 1:
                return UPLOAD_METHODS;
            case 0:
                return LIST_METHODS;
            default:
                return new ArrayList<>(0);
        }
    }

    /***
     * 用户的最大权限
     *  delete > update > upload > list
     *     3   >   2    >   1    >   0
     * @return delete/update/upload/list
     */
    @Cacheable(value = "allowMethods", key = "#username")
    public int maxAuthority(String username){
        List<String> authorities = CaffeineUtil.getAuthoritiesCache(username);
        if (authorities == null) {
            authorities = userService.getAuthorities(username);
        }
        int maxAuthority = 0;
        for (String authority : authorities) {
            if (authority.startsWith("cloud:file:")) {
                switch (authority) {
                    case "cloud:file:delete":
                        maxAuthority = 3;
                        break;
                    case "cloud:file:update":
                        if(maxAuthority < 2){
                            maxAuthority = 2;
                        }
                        break;
                    case "cloud:file:upload":
                        if(maxAuthority < 1){
                            maxAuthority = 1;
                        }
                        break;
                    case "cloud:file:list":
                    case "cloud:file:download":
                    default:
                        break;
                }
            }
        }
        return maxAuthority;
    }

    @Override
    public String getRealm(String host) {
        return realm;
    }

    public void setDigestGenerator(DigestGenerator digestGenerator) {
        this.digestGenerator = digestGenerator;
    }

    @Override
    public boolean isDigestAllowed() {
        return digestGenerator != null;
    }

    public DigestGenerator getDigestGenerator() {
        return digestGenerator;
    }
}
