package com.jmal.clouddisk.webdav.resource;

import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.util.CaffeineUtil;
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
import java.util.Map;
import java.util.Set;

/**
 * @author jmal
 * @Description FileResourceSet
 * @date 2023/3/27 11:47
 */
@Slf4j
public class FileResourceSet extends AbstractFileResourceSet {

    private final Map<String, IOssService> ossStorageServiceMap;

    public FileResourceSet(WebResourceRoot root, String base, Map<String, IOssService> ossStorageServiceMap) {
        super("/");
        setRoot(root);
        setWebAppMount("/");
        setBase(base);
        this.ossStorageServiceMap = ossStorageServiceMap;
    }


    @Override
    public WebResource getResource(String path) {
        log.info("getResource: {}", path);
        checkPath(path);
        WebResourceRoot root = getRoot();
        File f;
        Path prePath = Paths.get(path);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null && prePath.getNameCount() > 2) {
            path = path.substring(ossPath.length() + 1);
            FileInfo fileInfo = getOssStorageService(ossPath).getFileInfo(path);
            if (fileInfo == null) {
                return new EmptyResource(root, path);
            }
            return new OssFileResource(root, path, fileInfo, isReadOnly(), getManifest(), getOssStorageService(ossPath));
        } else {
            f = file(path, false);
        }
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

    private IOssService getOssStorageService(String ossPath) {
        BucketInfo bucketInfo = CaffeineUtil.getOssDiameterPrefixCache(ossPath);
        return this.ossStorageServiceMap.get(bucketInfo.getPlatform().getKey());
    }

    @Override
    public String[] list(String path) {
        checkPath(path);

        File f;
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            String name = getObjectName(path, ossPath);
            return getOssStorageService(ossPath).list(name);
        } else {
            f = file(path, true);
        }

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
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            String name = getObjectName(path, ossPath);
            return getOssStorageService(ossPath).mkdir(ossPath, name);
        } else {
            f = file(path, false);
        }

        if (f == null) {
            return false;
        }
        return f.mkdir();
    }

    private static String getObjectName(String path, String ossPath) {
        Path prePath = Paths.get(path);
        String name = "";
        if (prePath.getNameCount() > 2) {
            name = path.substring(ossPath.length() + 1);
            if (!name.endsWith("/")) {
                name = name + "/";
            }
        }
        return name;
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

        if (path.endsWith("/")) {
            return false;
        }

        File dest;
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            Path prePath = Paths.get(path);
            if (prePath.getNameCount() > 2) {
                path = path.substring(ossPath.length() + 1);
            }
            return getOssStorageService(ossPath).write(is, ossPath, path);
        } else {
            dest = file(path, false);
        }

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
