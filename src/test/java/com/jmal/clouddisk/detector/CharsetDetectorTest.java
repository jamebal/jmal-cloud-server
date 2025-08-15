package com.jmal.clouddisk.detector;

import cn.hutool.core.io.CharsetDetector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class CharsetDetectorTest {
    // 这里可以添加测试方法来验证 CharsetDetector 的功能
    // 例如，测试不同编码的文本是否能被正确检测到

    @Test
    public void testDetectCharset() {
        File file = new File("src/main/resources/banner.txt");
        String charset = String.valueOf(CharsetDetector.detect(file, StandardCharsets.UTF_8));
        assertEquals("UTF-8", charset);
    }
}
