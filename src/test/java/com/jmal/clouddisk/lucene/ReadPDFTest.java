package com.jmal.clouddisk.lucene;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ReadPDFTest {

    @Autowired
    private ReadPDFContentService readPDFContentService;

    @Test
    public void testImagePDF() {

        // dev环境下设置tesseract的lib路径
        Path tesseractLibPath = Paths.get("/opt/homebrew/Cellar/tesseract/5.3.4_1/lib");
        if (tesseractLibPath.toFile().exists()) {
            System.setProperty("jna.library.path", tesseractLibPath.toString());
        }

        File file = new File("/Users/jmal/Downloads/1hyflld.pdf");
        assertNotNull(file, "File should not be null");
        assertTrue(file.exists(), "File should exist");

        String content = readPDFContentService.read(file);
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

        String content = readPDFContentService.read(file);
        assertNotNull(content, "Content should not be null");
        assertFalse(content.isEmpty(), "Content should not be empty");

        // Assuming you expect some specific content
        // assertTrue(content.contains("Expected Content"), "Content should contain expected text");
    }
}
