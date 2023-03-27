package com.jmal.clouddisk.webdav.resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;
import org.apache.catalina.webresources.AbstractFileResourceSet;
import org.apache.catalina.webresources.EmptyResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * @author jmal
 * @Description FileResourceSet
 * @date 2023/3/27 11:47
 */
@Slf4j
public class FileResourceSet extends AbstractFileResourceSet {

    public FileResourceSet(WebResourceRoot root, String base) {
        super("/");
        setRoot(root);
        setWebAppMount("/");
        setBase(base);
    }


    @Override
    public WebResource getResource(String path) {
        checkPath(path);
        WebResourceRoot root = getRoot();
        File f;
        if (path.startsWith("/jmal/aliyunoss")) {
            Path prePath = Paths.get(path);
            if (prePath.getNameCount() == 2) {
                path = "/";
            } else {
                path = path.substring("/jmal/aliyunoss".length());
            }
            String name = "";
            if (!path.equals("/")) {
                name = path;
            }
            f = new File("/Users/jmal/Downloads/jpom-2.10.29/agent-2.10.29-release", name);
        } else {
            f = file(path, false);
        }
        // File f = file(path, false);
        if (f == null) {
            return new EmptyResource(root, path);
        }
        if (!f.exists()) {
            return new EmptyResource(root, path, f);
        }
        if (f.isDirectory() && path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        return new LocalFileResource(root, path, f, isReadOnly(), getManifest());
    }

    @Override
    public String[] list(String path) {
        checkPath(path);

        File f;
        if (path.startsWith("/jmal/aliyunoss")) {
            Path prePath = Paths.get(path);
            if (prePath.getNameCount() == 2) {
                path = "/";
            } else {
                path = path.substring("/jmal/aliyunoss".length());
            }
            String name = "";
            if (!path.equals("/")) {
                name = path;
            }
            f = new File("/Users/jmal/Downloads/jpom-2.10.29/agent-2.10.29-release", name);
        } else {
            f = file(path, true);
        }

        // File f = file(path, true);
        if (f == null) {
            return EMPTY_STRING_ARRAY;
        }
        String[] result = f.list();
        if (result == null) {
            return EMPTY_STRING_ARRAY;
        } else {
            return result;
        }
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        return new ResourceSet<>();
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);
        if (isReadOnly()) {
            return false;
        }

        File f;
        if (path.startsWith("/jmal/aliyunoss")) {
            Path prePath = Paths.get(path);
            if (prePath.getNameCount() == 2) {
                path = "/";
            } else {
                path = path.substring("/jmal/aliyunoss".length());
            }
            String name = "";
            if (!path.equals("/")) {
                name = path;
            }
            f = new File("/Users/jmal/Downloads/jpom-2.10.29/agent-2.10.29-release", name);
        } else {
            f = file(path, false);
        }

        // File f = file(path, false);
        if (f == null) {
            return false;
        }
        return f.mkdir();
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException(
                    sm.getString("dirResourceSet.writeNpe"));
        }

        if (isReadOnly()) {
            return false;
        }

        // write() is meant to create a file so ensure that the path doesn't
        // end in '/'
        if (path.endsWith("/")) {
            return false;
        }

        File dest;
        if (path.startsWith("/jmal/aliyunoss")) {
            Path prePath = Paths.get(path);
            if (prePath.getNameCount() == 2) {
                path = "/";
            } else {
                path = path.substring("/jmal/aliyunoss".length());
            }
            String name = "";
            if (!path.equals("/")) {
                name = path;
            }
            dest = new File("/Users/jmal/Downloads/jpom-2.10.29/agent-2.10.29-release", name);
        } else {
            dest = file(path, false);
        }

        // File dest = file(path, false);
        if (dest == null) {
            return false;
        }

        if (dest.exists() && !overwrite) {
            return false;
        }

        try {
            if (overwrite) {
                Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(is, dest.toPath());
            }
        } catch (IOException ioe) {
            return false;
        }

        return true;
    }

    @Override
    protected void checkType(File file) {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException(sm.getString("dirResourceSet.notDirectory",
                    getBase(), File.separator, getInternalPath()));
        }
    }

}
