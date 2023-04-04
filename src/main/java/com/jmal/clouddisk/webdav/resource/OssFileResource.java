package com.jmal.clouddisk.webdav.resource;

import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssInputStream;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.AbstractResource;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.jar.Manifest;
import java.util.zip.CheckedInputStream;

public class OssFileResource extends AbstractResource {

    private static final Log log = LogFactory.getLog(OssFileResource.class);

    private static final boolean PROPERTIES_NEED_CONVERT;
    static {
        boolean isEBCDIC = false;
        try {
            String encoding = System.getProperty("file.encoding");
            if (encoding.contains("EBCDIC")) {
                isEBCDIC = true;
            }
        } catch (SecurityException e) {
            // Ignore
        }
        PROPERTIES_NEED_CONVERT = isEBCDIC;
    }


    private final FileInfo resource;
    private final String name;
    private final boolean readOnly;
    private final Manifest manifest;
    private final boolean needConvert;

    private final IOssService ossStorageService;

    public OssFileResource(WebResourceRoot root, String webAppPath, FileInfo resource, boolean readOnly, Manifest manifest, IOssService ossStorageService) {
        super(root,webAppPath);
        this.ossStorageService = ossStorageService;
        this.resource = resource;
        if (webAppPath.charAt(webAppPath.length() - 1) == '/') {
            String realName = resource.getName() + '/';
            if (webAppPath.endsWith(realName)) {
                name = resource.getName();
            } else {
                // This is the root directory of a mounted ResourceSet
                // Need to return the mounted name, not the real name
                int endOfName = webAppPath.length() - 1;
                name = webAppPath.substring(
                        webAppPath.lastIndexOf('/', endOfName - 1) + 1,
                        endOfName);
            }
        } else {
            // Must be a file
            name = resource.getName();
        }

        this.readOnly = readOnly;
        this.manifest = manifest;
        this.needConvert = PROPERTIES_NEED_CONVERT && name.endsWith(".properties");
    }

    @Override
    public long getLastModified() {
        return resource.getLastModified().getTime();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public boolean isDirectory() {
        return resource.getKey().endsWith("/");
    }

    @Override
    public boolean isFile() {
        return !isDirectory();
    }

    @Override
    public boolean delete() {
        if (readOnly) {
            return false;
        }
        return ossStorageService.delete(resource.getKey());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return getContentLengthInternal(needConvert);
    }

    private long getContentLengthInternal(boolean convert) {
        if (convert) {
            byte[] content = getContent();
            if (content == null) {
                return -1;
            } else {
                return content.length;
            }
        }

        if (isDirectory()) {
            return -1;
        }
        return resource.getSize();
    }

    @Override
    public String getCanonicalPath() {
        return "";
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    protected InputStream doGetInputStream() {
        if (needConvert) {
            byte[] content = getContent();
            if (content == null) {
                return null;
            } else {
                return new ByteArrayInputStream(content);
            }
        }
        AbstractOssObject object = this.ossStorageService.getObject(resource.getKey());
        if (object == null) {
            return null;
        }
        InputStream inputStream;
        try {
            inputStream = object.getInputStream();
        } catch (FileNotFoundException e) {
            return null;
        }
        OssInputStream ossInputStream;
        if (inputStream instanceof CheckedInputStream checkedInputStream) {
            ossInputStream = new OssInputStream(inputStream, checkedInputStream.getChecksum());
            ossInputStream.setAbstractOssObject(object);
        } else {
            return inputStream;
        }
        return ossInputStream;
    }

    @Override
    public final byte[] getContent() {
        // Use internal version to avoid loop when needConvert is true
        long len = getContentLengthInternal(false);

        if (len > Integer.MAX_VALUE) {
            // Can't create an array that big
            throw new ArrayIndexOutOfBoundsException(sm.getString(
                    "abstractResource.getContentTooLarge", getWebappPath(),
                    Long.valueOf(len)));
        }

        if (len < 0) {
            // Content is not applicable here (e.g. is a directory)
            return null;
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (AbstractOssObject object = this.ossStorageService.getObject(resource.getKey())) {
            if (object == null) {
                return null;
            }
            InputStream is = object.getInputStream();
            while (pos < size) {
                int n = is.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractResource.getContentFail",
                        getWebappPath()), ioe);
            }

        }

        if (needConvert) {
            // Workaround for certain files on platforms that use
            // EBCDIC encoding, when they are read through FileInputStream.
            // See commit message of rev.303915 for original details
            // https://svn.apache.org/viewvc?view=revision&revision=303915
            String str = new String(result);
            try {
                result = str.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                result = null;
            }
        }
        return result;
    }


    @Override
    public long getCreation() {
        return resource.getLastModified().getTime();
    }

    @Override
    public URL getURL() {
        try {
            return new URL(resource.getKey());
        } catch (MalformedURLException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("fileResource.getUrlFail", resource.getKey()), e);
            }
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        if (getWebappPath().startsWith("/WEB-INF/classes/") && name.endsWith(".class")) {
            return getWebResourceRoot().getResource("/WEB-INF/classes/").getURL();
        } else {
            return getURL();
        }
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    protected Log getLog() {
        return log;
    }

    public FileInfo getFileInfo() {
        return resource;
    }
}
