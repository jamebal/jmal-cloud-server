package com.jmal.clouddisk.util;

import com.jmal.clouddisk.exception.CommonException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsrfProtectionUtilTest {
    @Test
    void allowsPublicHttpAndHttpsUrls() {
        assertEquals("https://8.8.8.8/image.png", SsrfProtectionUtil.checkUrlSafe("https://8.8.8.8/image.png"));
        assertEquals("http://1.1.1.1/image.png", SsrfProtectionUtil.checkUrlSafe("http://1.1.1.1/image.png"));
    }

    @Test
    void rejectsNonHttpSchemesAndCredentials() {
        assertRejected("file:///etc/passwd");
        assertRejected("ftp://8.8.8.8/image.png");
        assertRejected("http://user:password@8.8.8.8/image.png");
    }

    @Test
    void rejectsNonPublicAddressRanges() {
        for (String host : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "[::1]", "[fc00::1]", "[fe80::1]"}) {
            assertRejected("http://" + host + "/resource");
        }
    }

    private static void assertRejected(String url) {
        assertThrows(CommonException.class, () -> SsrfProtectionUtil.checkUrlSafe(url), url);
    }
}
