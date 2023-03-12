package com.jmal.clouddisk;

import java.io.UnsupportedEncodingException;

public class CharacterSetTest {
    public static void main(String[] args) throws UnsupportedEncodingException {
        String input = "some string with non-ASCII characters 屏";
        byte[] isoBytes = input.getBytes("ISO-8859-1");
    }
}
