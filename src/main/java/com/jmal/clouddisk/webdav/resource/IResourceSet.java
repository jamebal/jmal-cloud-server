package com.jmal.clouddisk.webdav.resource;

import org.apache.catalina.WebResource;

import java.io.InputStream;

/**
 * @author jmal
 * @Description IResourceSet
 * @date 2023/3/27 11:03
 */
public interface IResourceSet {
    WebResource getResource(String path);

    String[] list(String path);

    boolean mkdir(String path);

    boolean write(String path, InputStream is, boolean overwrite);
}
