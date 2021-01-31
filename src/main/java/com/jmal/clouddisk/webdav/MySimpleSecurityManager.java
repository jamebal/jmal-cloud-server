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
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description webDAV 认证授权
 * @Date 2021/1/29 2:03 下午
 */
@Component
@Slf4j
public class MySimpleSecurityManager implements io.milton.http.SecurityManager{

    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private FileProperties fileProperties;
    private String realm;
    private DigestGenerator digestGenerator;

    public MySimpleSecurityManager() {
        realm = "userRealm";
        digestGenerator = new DigestGenerator();
    }

    public MySimpleSecurityManager( DigestGenerator digestGenerator ) {
        this.digestGenerator = digestGenerator;
    }

    public MySimpleSecurityManager(String realm) {
        this.realm = realm;
        digestGenerator = new DigestGenerator();
    }

    @Override
    public Object authenticate( String user, String password ) {
        log.debug( "authenticate: " + user + " - " + password);
        // user name will include domain when coming form ftp. we just strip it off
        if (user.contains( "@")) {
            user = user.substring( 0, user.indexOf( "@"));
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
    public Object authenticate( DigestResponse digestRequest ) {
        if( digestGenerator == null ) {
            throw new RuntimeException("No digest generator is configured");
        }
        String username = digestRequest.getUser();
        String uri = digestRequest.getUri().replaceFirst(fileProperties.getWebDavPrefixPath(),"");
        Console.log(uri);
        Path path = Paths.get(uri);
        if(path.getNameCount() < 1){
            return null;
        }
        if(!username.equals(path.getName(0).toString())){
            return null;
        }
        String actualPassword = userService.getPasswordByUserName(username);
        String serverResponse = digestGenerator.generateDigest( digestRequest, actualPassword );
        String clientResponse = digestRequest.getResponseDigest();
        if( serverResponse.equals( clientResponse ) ) {
            return "ok";
        } else {
            return null;
        }
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth, Resource resource ) {
        // switch(method) {
        //     case GET: return true;
        //     case HEAD: return true;
        //     case OPTIONS: return true;
        //     case PROPFIND: return true;
        // }
        if (auth != null) {
            List<Request.Method> methods = getMethods(auth);
            if (methods.contains(method)) {
                return auth.getTag() != null;
            }
        }
        return false;
    }

    private List<Request.Method> getMethods(Auth auth) {
        List<Request.Method> methods = new ArrayList<>();
        String username = auth.getUser();
        List<String> authorities = CaffeineUtil.getAuthoritiesCache(username);
        if(authorities == null){
            authorities = userService.getAuthorities(username);
        }
        if(authorities.contains("cloud:file:delete")){
            methods.addAll(Arrays.asList(Request.Method.values()));
            return methods;
        }
        for (String authority : authorities) {
            if("cloud:file:list".equals(authority) || "cloud:file:download".equals(authority)){
                methods.addAll(Arrays.asList(Request.Method.GET, Request.Method.HEAD, Request.Method.OPTIONS, Request.Method.PROPFIND));
                return methods;
            }
            if("cloud:file:upload".equals(authority)){
                methods.addAll(Arrays.asList(Request.Method.PUT, Request.Method.POST));
                return methods;
            }
            if("cloud:file:update".equals(authority)){
                methods.addAll(Arrays.asList(Request.Method.PUT, Request.Method.MOVE, Request.Method.COPY, Request.Method.LOCK, Request.Method.UNLOCK));
                return methods;
            }
        }
        return methods;
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
