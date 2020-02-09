package com.jmal.clouddisk;
import java.io.IOException;

class Zip {
    public static void main(String[] args) throws IOException {
        long s = System.currentTimeMillis();
        CompressUtils.zip("/Users/jmal/temp/filetest/rootpath/jmal/pap.er/jmal","/Users/jmal/temp/filetest/rootpath/jmal/pap.er/jmal.zip");
        System.out.println(System.currentTimeMillis() - s);
    }
}
