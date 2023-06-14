package com.jmal.clouddisk.lucene;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.FileInputStream;
import java.io.IOException;

public class ReadPPTX {
    public static void main(String[] args) {
        try {
            FileInputStream fis = new FileInputStream("/Users/jmal/Downloads/未命名文件.pptx");
            XMLSlideShow ppt = new XMLSlideShow(fis);

            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        System.out.println(textShape.getText());
                    }
                }
            }

            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
