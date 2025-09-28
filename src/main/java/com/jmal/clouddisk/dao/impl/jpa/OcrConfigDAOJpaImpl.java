package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.IOcrConfigDAO;
import com.jmal.clouddisk.dao.write.IWriteService;
import com.jmal.clouddisk.dao.write.ocrconfig.OcrConfigOperation;
import com.jmal.clouddisk.dao.repository.jpa.OcrConfigRepository;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.ocr.OcrConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OcrConfigDAOJpaImpl implements IOcrConfigDAO, IWriteCommon<OcrConfig> {

    private final OcrConfigRepository ocrConfigRepository;
    private final IWriteService writeService;

    @Override
    public void AsyncSaveAll(Iterable<OcrConfig> entities) {
        try {
            writeService.submit(new OcrConfigOperation.CreateAll(entities)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public OcrConfig findOcrConfig() {
        return ocrConfigRepository.findAll().stream().findFirst().orElse(null);
    }

    @Override
    public void save(OcrConfig config) {
        try {
            writeService.submit(new OcrConfigOperation.CreateAll(Collections.singleton(config))).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }
}
