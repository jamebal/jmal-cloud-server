package com.jmal.clouddisk.media;

import cn.hutool.core.convert.Convert;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.Constants;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImageMagickProcessorTest {

    @Autowired
    private FileProperties fileProperties;

    private Path tempDir;

    @BeforeAll
    public void setup() throws IOException {
        tempDir = fileProperties.getTestTempDirPath();
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
    }

    @AfterAll
    public void cleanup() {
        //FileUtil.del(tempDir);
    }

    public static void main(String[] args) {
    }

    @Test
    public void testGenerateThumbnail() throws IOException, InterruptedException, URISyntaxException {
        cropImageTest("file/images/Multiavatar-Randall Zone.png");
        cropImageTest("file/images/test.svg");
        cropImageTest("file/images/sample_640×426.bmp");
        cropImageTest("file/images/sample1.heic");
        cropImageTest("file/images/sample_640×426.gif");
        cropImageTest("file/images/sample1.heif");
        cropImageTest("file/images/sample1.dng");
    }

    private File getResourcesFile(String classpath) throws URISyntaxException {
        URL resourceRootUrl = getClass().getClassLoader().getResource(classpath);
        if (resourceRootUrl == null) {
            throw new IllegalArgumentException("测试文件未找到，请确保路径正确");
        }
        return Paths.get(resourceRootUrl.toURI()).toFile();
    }


    private void cropImageTest(String classpath) throws IOException, InterruptedException, URISyntaxException {

        File resourcesFile = getResourcesFile(classpath);
        ImageMagickProcessor.ImageFormat srcImageFormat = ImageMagickProcessor.identifyFormat(resourcesFile);

        int targetWidth = 256;

        if (srcImageFormat.getWidth() < targetWidth) {
            targetWidth = srcImageFormat.getWidth();
        }

        // 将生成的缩略图保存到临时目录
        File thumbnailFile = tempDir.resolve(resourcesFile.getName() + Constants.POINT_SUFFIX_WEBP).toFile();

        FileOutputStream fileOutputStream = new FileOutputStream(thumbnailFile);

        ImageMagickProcessor.cropImage(resourcesFile, "1", Convert.toStr(targetWidth), null, fileOutputStream);
        // 验证缩略图文件是否存在, 并且是否为一个正常的图片
        ImageMagickProcessor.ImageFormat imageFormat = ImageMagickProcessor.identifyFormat(thumbnailFile);
        Assertions.assertNotNull(imageFormat, "缩略图生成失败，文件可能不存在或不是有效的图片格式");
        Assertions.assertEquals(targetWidth, imageFormat.getWidth(), "缩略图宽度不正确");
    }

}
