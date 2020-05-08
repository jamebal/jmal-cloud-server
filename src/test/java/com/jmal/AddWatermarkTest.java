package com.jmal;

import cn.hutool.core.img.Img;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ResourceUtils;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * @Description 添加水印
 * @Author jmal
 * @Date 2020-04-27 09:48
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class AddWatermarkTest {

    @Test
    public void add() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:mover-icon.png");
        long stime = System.currentTimeMillis();
        Font font = new Font("Courier", Font.PLAIN, 24);
        Color color = new Color(64, 158, 255);
        for (int i = 1; i < 100; i++) {
            Img img = Img.from(file);
            Img after = img.pressText(i+"", color, font,12,13, 1.0f);
            after.write(new File("/Users/jmal/temp/move-file/move-file"+i+".png"));
        }
    }
}
