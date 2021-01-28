package com.jmal.clouddisk.webdav;

import io.milton.http.Auth;
import io.milton.http.AuthenticationHandler;
import io.milton.http.Request;
import io.milton.resource.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author jmal
 * @Description TODO
 * @Date 2021/1/28 5:14 下午
 */
@Component
public class MyAuthorizationHandler implements AuthenticationHandler {

    @Override
    public boolean supports(Resource r, Request request) {
        System.out.println(request.getHeaders());
        Auth auth = request.getAuthorization();
        if( auth == null ) {
            return false;
        }
        return auth.getScheme().equals(Auth.Scheme.BASIC);
    }

    @Override
    public Object authenticate(Resource resource, Request request) {
        Auth auth = request.getAuthorization();
        if(auth != null){
            System.err.println("url:"+request.getAbsolutePath()+",user:" + auth.getUser() +", pwd:"+auth.getPassword()+", ");
            if("user".equals(auth.getUser()) && "pwd".equals(auth.getPassword())){
                return "ok";
            }
        }
        return null;
    }

    @Override
    public void appendChallenges(Resource resource, Request request, List<String> challenges) {
        System.err.println("appendChallenges");
    }

    @Override
    public boolean isCompatible(Resource resource, Request request) {
        System.err.println("isCompatible");
        return false;
    }

    @Override
    public boolean credentialsPresent(Request request) {
        System.err.println("credentialsPresent");
        return false;
    }
}
