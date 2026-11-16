package com.jmal.clouddisk.util;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class SsrfProtectionUtil {
    private SsrfProtectionUtil() {
    }

    public static String checkUrlSafe(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || uri.getRawUserInfo() != null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException();
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] bytes = address.getAddress();
                boolean mappedPrivateIpv4 = bytes.length == 16
                        && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0
                        && bytes[4] == 0 && bytes[5] == 0 && bytes[6] == 0 && bytes[7] == 0
                        && bytes[8] == 0 && bytes[9] == 0 && bytes[10] == -1 && bytes[11] == -1
                        && isPrivateIpv4(bytes[12], bytes[13]);
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || mappedPrivateIpv4
                        || (bytes.length == 4 && isPrivateIpv4(bytes[0], bytes[1]))
                        || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)) {
                    throw new IllegalArgumentException();
                }
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException | UnknownHostException e) {
            throw new CommonException(ExceptionType.ACCESS_FORBIDDEN);
        }
    }

    private static boolean isPrivateIpv4(byte first, byte second) {
        return (first & 0xff) == 100 && (second & 0xc0) == 0x40;
    }
}
