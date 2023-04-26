package com.jmal.clouddisk.javacv;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.lang.Console;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class VideoThumbnailExtractor {
    public static void main(String[] args) throws IOException {
        // String url = "https://my.jmal.top:9932/jmalcloud/%E8%BF%88%E5%85%8B%E5%B0%94%C2%B7%E6%9D%B0%E5%85%8B%E9%80%8A%E7%9A%84%E5%A4%A9%E8%B5%8B%E5%92%8C%E9%94%99%E5%A4%B1%E7%9A%84%E8%89%AF.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=X6ZS72949MTES2IKJ6VA%2F20230426%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230426T141736Z&X-Amz-Expires=604800&X-Amz-Security-Token=eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJhY2Nlc3NLZXkiOiJYNlpTNzI5NDlNVEVTMklLSjZWQSIsImV4cCI6MTY4MjU2MTQyOSwicGFyZW50Ijoiam1hbCJ9.VqLlG1SjbXfSD2ROYjVWkDBIk3I2zGHvYrayIVxQdzv4qqcavhezmu7hnPUwHR-zxBXrwNnty-MzQVoHJ2-mXQ&X-Amz-SignedHeaders=host&versionId=null&X-Amz-Signature=f950737e6fb13c99083d313b18f8c37991b4da635d572d779312044cfd2bb5d6";
        String videoPath = "/Users/jmal/Pictures/截图/屏幕录制 1.mov";
        String outputPath = "/Users/jmal/Downloads/thumbnail1.jpg";

        extractThumbnail(videoPath, outputPath);
    }

    public static void extractThumbnail(String videoPath, String outputPath) throws IOException {
        TimeInterval timeInterval = new TimeInterval();
        FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber(videoPath);
        frameGrabber.start();

        // 跳过前面的帧，以防止出现黑屏封面
        int frameToSkip = 50;
        for (int i = 0; i < frameToSkip; i++) {
            frameGrabber.grab();
        }
        // 提取一帧作为封面
        Frame frame = frameGrabber.grabImage();
        Java2DFrameConverter converter = new Java2DFrameConverter();
        BufferedImage image = converter.getBufferedImage(frame);
        // 将封面图像保存到文件中
        ImageIO.write(image, "jpg", new File(outputPath));
        frameGrabber.stop();
        Console.log("耗时: ", timeInterval.intervalMs(), "ms");
    }
}




