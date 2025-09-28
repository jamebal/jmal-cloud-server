package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.ITranscodeConfigDAO;
import com.jmal.clouddisk.dao.write.IWriteService;
import com.jmal.clouddisk.dao.write.transcodecnofig.TranscodeConfigOperation;
import com.jmal.clouddisk.dao.repository.jpa.TranscodeConfigRepository;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.media.TranscodeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TranscodeConfigDAOJpaImpl implements ITranscodeConfigDAO, IWriteCommon<TranscodeConfig> {

    private final TranscodeConfigRepository transcodeConfigRepository;
    private final IWriteService writeService;

    @Override
    public void AsyncSaveAll(Iterable<TranscodeConfig> entities) {
        try {
            writeService.submit(new TranscodeConfigOperation.CreateAll(entities)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public TranscodeConfig findTranscodeConfig() {
        return transcodeConfigRepository.findAll().stream().findFirst().orElse(null);
    }

    @Override
    public void save(TranscodeConfig config) {
        try {
            writeService.submit(new TranscodeConfigOperation.CreateAll(Collections.singleton(config))).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }
}
