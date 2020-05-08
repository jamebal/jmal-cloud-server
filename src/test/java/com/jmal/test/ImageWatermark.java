package com.jmal.test;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;

import java.awt.*;
import java.io.File;

/**
 * @Description 图片水印
 * @Author jmal
 * @Date 2020-04-26 17:34
 */
public class ImageWatermark {

    public static void main(String[] args) {
        File file = new File("/Users/jmal/Downloads/文件 (2).png");
        long stime = System.currentTimeMillis();
        Font font = new Font("Courier", Font.PLAIN, 24);
        Color color = new Color(64, 158, 255);
        for (int i = 1; i < 100; i++) {
            Img img = Img.from(file);
            Img after = img.pressText(i+"", color, font,11,13, 1.0f);
            after.write(new File("/Users/jmal/temp/move-file/move-file"+i+".png"));
        }
        System.out.println(System.currentTimeMillis() - stime);
    }

}
