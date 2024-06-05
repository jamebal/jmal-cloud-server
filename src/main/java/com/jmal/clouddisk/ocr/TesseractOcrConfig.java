package com.jmal.clouddisk.ocr;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TessAPI;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Data
@Component
@Configuration
@ConfigurationProperties(prefix = "tess4j")
@Slf4j
public class TesseractOcrConfig {

    private String dataPath;

    @Bean
    public ThreadLocal<Tesseract> tesseractThreadLocal() {
        // 设置 Tess4J 的日志级别
        return ThreadLocal.withInitial(() -> {
            Tesseract tesseract = new Tesseract();
            // 设置数据文件夹路径
            tesseract.setDatapath(dataPath);
            // 设置为中文简体
            tesseract.setLanguage("chi_sim");
            tesseract.setOcrEngineMode(TessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
            tesseract.setPageSegMode(TessAPI.TessPageSegMode.PSM_AUTO);
            return tesseract;
        });
    }

}
