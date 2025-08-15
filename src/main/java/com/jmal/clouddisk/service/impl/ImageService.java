package com.jmal.clouddisk.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    /**
     * 生成缩略图
     *
     * @param file   File
     * @param update org.springframework.data.mongodb.core.query.UpdateDefinition
     */
    public void generateThumbnail(File file, Update update) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(file);
            thumbnail.size(256, 256);
            thumbnail.toOutputStream(out);
            update.set("content", out.toByteArray());
        } catch (UnsupportedFormatException e) {
            log.warn("{}{}", e.getMessage(), file.getAbsolutePath());
        } catch (Exception e) {
            log.error("{}{}", e.getMessage(), file.getAbsolutePath());
        } catch (Throwable e) {
            log.error(e.getMessage());
        }
    }

}
