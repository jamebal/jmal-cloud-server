package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.model.file.FileDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class CoverFileService {

    private final ImageMagickProcessor imageMagickProcessor;

    private final IFileDAO fileDAO;

    private final DataSourceProperties dataSourceProperties;

    /**
     * 更新文件封面
     *
     * @param fileId    文件Id
     * @param coverFile 封面文件
     */
    public void updateCoverFileDocument(String fileId, File coverFile) {
        if (coverFile == null || !coverFile.exists()) {
            return;
        }
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(fileId);
        imageMagickProcessor.generateThumbnail(coverFile, fileDocument);
        if (dataSourceProperties.getType() == DataSourceType.mongodb) {
            fileDAO.setContent(fileId, fileDocument.getContent());
        }
        fileDAO.setShowCover(fileId, true);
    }

}
