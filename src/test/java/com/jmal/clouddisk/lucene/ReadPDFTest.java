package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.util.TesseractUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ReadPDFTest {

    @Autowired
    private ReadContentService readContentService;

    @Test
    public void testImagePDF() {

        // dev环境下设置tesseract的lib路径
        TesseractUtil.setTesseractLibPath();

        File file = new File("/Users/jmal/Downloads/1hyflld.pdf");
        assertNotNull(file, "File should not be null");
        assertTrue(file.exists(), "File should exist");

        String content = readContentService.readPdfContent(file, null);
        assertNotNull(content, "Content should not be null");
        assertFalse(content.isEmpty(), "Content should not be empty");

        // Assuming you expect some specific content
        // assertTrue(content.contains("Expected Content"), "Content should contain expected text");
    }

    @Test
    public void testTextPDF() {

        File file = new File("/Users/jmal/Downloads/h11ssl-nc.pdf");
        assertNotNull(file, "File should not be null");
        assertTrue(file.exists(), "File should exist");

        String content = readContentService.readPdfContent(file, null);
        assertNotNull(content, "Content should not be null");
        assertFalse(content.isEmpty(), "Content should not be empty");

        // Assuming you expect some specific content
        // assertTrue(content.contains("Expected Content"), "Content should contain expected text");
    }
}
