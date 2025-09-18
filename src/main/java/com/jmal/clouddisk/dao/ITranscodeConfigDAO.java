package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.media.TranscodeConfig;

public interface ITranscodeConfigDAO {

    TranscodeConfig findTranscodeConfig();

    void save(TranscodeConfig config);
}
