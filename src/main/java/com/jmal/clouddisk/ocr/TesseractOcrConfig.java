package com.jmal.clouddisk.ocr;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Data
@Component
@Configuration
@ConfigurationProperties(prefix = "tess4j")
@Slf4j
public class TesseractOcrConfig {

    private String dataPath;

}
