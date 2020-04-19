package com.jmal.test;

public class GCDemo {
    public static void main(String[] args) throws InterruptedException {
        String s1 = "a"+"b"+"c";
        String s2 = "ab";
        String s3 = s2 + "c";
        System.out.println(s1 == s2);
    }
}
