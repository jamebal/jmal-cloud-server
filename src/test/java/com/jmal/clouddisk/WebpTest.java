package com.jmal.clouddisk;

import com.luciad.imageio.webp.WebPWriteParam;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * @author jmal
 * @Description webp
 * @Date 2020/12/24 10:20 上午
 */
public class WebpTest {
    public static void main(String args[]) throws IOException {
        String inputJpgPath = "/Users/jmal/Downloads/bolg/favicon.ico";
        String outputWebpPath = "/Users/jmal/Downloads/bolg/favicon.ico.webp";

        // 从某处获取图像进行编码
        BufferedImage image = ImageIO.read(new File(inputJpgPath));

        // 获取一个WebP ImageWriter实例
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();

        // 配置编码参数
        WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
        writeParam.setCompressionMode(WebPWriteParam.MODE_DEFAULT);

        // 在ImageWriter上配置输出
        writer.setOutput(new FileImageOutputStream(new File(outputWebpPath)));

        // 编码
        writer.write(null, new IIOImage(image, null, null), writeParam);

    }
}