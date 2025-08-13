package com.jmal.clouddisk.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.File;
import java.io.IOException;

public class HashUtil {

    private static final HashFunction SHA256 = Hashing.sha256();

    public static String sha256(File file) {
        try {
            return com.google.common.io.Files.asByteSource(file)
                    .hash(SHA256)
                    .toString();
        } catch (IOException e) {
            throw new RuntimeException("Error reading file: " + file.getAbsolutePath(), e);
        }
    }

    public static String sha256(String str) {
        return SHA256.hashUnencodedChars(str).toString();
    }
}
