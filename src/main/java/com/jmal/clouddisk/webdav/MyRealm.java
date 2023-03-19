package com.jmal.clouddisk.webdav;

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

public class MyRealm extends RealmBase {
    @Override
    protected String getPassword(String username) {
        return "asdfg";
    }

    @Override
    protected Principal getPrincipal(String username) {
        List<String> list = new ArrayList<>();
        list.add("jmal");
        return new GenericPrincipal(username, list);
    }
}
