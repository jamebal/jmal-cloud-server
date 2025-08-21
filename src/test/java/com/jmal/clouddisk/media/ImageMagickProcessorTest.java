package com.jmal.clouddisk.media;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.config.FileProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImageMagickProcessorTest {

    @Autowired
    private FileProperties fileProperties;

    private Path tempDir;

    private File testFile;

    @BeforeAll
    public void setup() throws IOException {
        tempDir = fileProperties.getTestTempDirPath();
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
        // 生成测试文件
        testFile = genPNG();
    }

    @AfterAll
    public void cleanup() {
        //FileUtil.del(tempDir);
    }

    @Test
    public void testGenerateThumbnail() throws IOException, InterruptedException {
        Path tempPath = fileProperties.getTestTempDirPath();
        // 确保测试临时目录存在
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath);
        }

        // 将生成的缩略图保存到临时目录
        File thumbnailFile = tempPath.resolve("thumbnail.png").toFile();

        // 测试生成缩略图
        try (InputStream inputStream = ImageMagickProcessor.cropImage(new File("/Users/jmal/temp/filetest/rootpath/jmal/未命名文件夹/IMG_0871_01.HEIC.jpg"), "1", "256", "256")) {
            FileUtil.writeFromStream(inputStream, thumbnailFile);
        }
        // 验证缩略图文件是否存在, 并且是否为一个正常的图片
        BufferedImage thumbnailImage = ImageIO.read(thumbnailFile);
        Assertions.assertNotNull(thumbnailImage, "缩略图生成失败，文件可能不存在或不是有效的图片格式");
        // 验证缩略图的尺寸
        Assertions.assertEquals(256, thumbnailImage.getWidth(), "缩略图宽度不正确");
        Assertions.assertEquals(256, thumbnailImage.getHeight(), "缩略图高度不正确");
    }

    /**
     * 生成一个简单的SVG文件用于测试
     */

    private File genSVG() throws IOException {
        File svgFile = fileProperties.getTestTempDirPath().resolve("test.svg").toFile();
        String svgContent = """
                <svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="50" cy="50" r="40" stroke="green" fill="yellow" />
                </svg>
                """;
        Files.writeString(svgFile.toPath(), svgContent, StandardCharsets.UTF_8);
        return svgFile;
    }

    private File genJPG() throws IOException {
        File jpgFile = fileProperties.getTestTempDirPath().resolve("test.jpg").toFile();
        BufferedImage image = genImage();
        ImageIO.write(image, "jpg", jpgFile);
        return jpgFile;
    }

    private File genBMP() throws IOException {
        File bmpFile = fileProperties.getTestTempDirPath().resolve("test.bmp").toFile();
        BufferedImage image = genImage();
        ImageIO.write(image, "bmp", bmpFile);
        return bmpFile;
    }

    private File genPNG() throws IOException {
        File pngFile = fileProperties.getTestTempDirPath().resolve("test.png").toFile();
        BufferedImage image = genImage();
        ImageIO.write(image, "png", pngFile);
        return pngFile;
    }

    private File genGIF() throws IOException {
        File gifFile = fileProperties.getTestTempDirPath().resolve("test.gif").toFile();
        BufferedImage image = genImage();
        ImageIO.write(image, "gif", gifFile);
        return gifFile;
    }

    private BufferedImage genImage() {
        BufferedImage image = new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(10, 10, 2028, 2028);
        g2d.dispose();
        return image;
    }

}
