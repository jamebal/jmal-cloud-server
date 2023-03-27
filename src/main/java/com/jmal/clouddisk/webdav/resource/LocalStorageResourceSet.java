package com.jmal.clouddisk.webdav.resource;

import org.apache.catalina.WebResource;

import java.io.InputStream;

/**
 * @author jmal
 * @Description 本地存储
 * @date 2023/3/27 15:29
 */
public class LocalStorageResourceSet implements IResourceSet{
    @Override
    public WebResource getResource(String path) {
        return null;
    }

    @Override
    public String[] list(String path) {
        return new String[0];
    }

    @Override
    public boolean mkdir(String path) {
        return false;
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        return false;
    }
}
