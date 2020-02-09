package com.jmal.clouddisk;


/**
 * @Description mongodbZipFiles
 * @Author jmal
 * @Date 2020-02-06 19:54
 */
public class mongodbZipFiles {

    public static void main(String[] args) {
        String parentPath = "/图片/Pictures/";
        String path = "/图片/Pictures/截图/";

        System.out.println(path.substring(parentPath.length()));
    }
}
