package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.ocr.OcrConfig;

public interface IOcrConfigDAO {

    OcrConfig findOcrConfig();

    void save(OcrConfig config);
}
