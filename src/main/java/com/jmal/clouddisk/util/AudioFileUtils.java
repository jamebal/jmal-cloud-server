package com.jmal.clouddisk.util;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.model.Music;
import com.jmal.clouddisk.model.file.FileDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.datatype.Artwork;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyAPIC;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTALB;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTIT2;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTPE1;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/***
 * 读取音频文件信息工具
 * @author jmal
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioFileUtils {

    private final FilePersistenceService filePersistenceService;
    private final DataSourceProperties dataSourceProperties;

    private static final String SONGNAME_TAG = "TIT2";
    private static final String SINGER_TAG = "TPE1";
    private static final String ALBUM_TAG = "TALB";
    private static final String COVER_TAG = "APIC";

    public Music readAudio(FileDocument fileDocument, File file) {
        Music music = new Music();
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            Tag audioFileTag = audioFile.getTag();
            if (audioFileTag instanceof FlacTag tag) {
                System.out.println(tag.getFirst(FieldKey.TITLE));
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null) {
                    if (dataSourceProperties.getType() == DataSourceType.mongodb) {
                        String Base64 = ImgUtil.toBase64(ImgUtil.toImage(artwork.getBinaryData()), artwork.getMimeType());
                        music.setCoverBase64(Base64);
                    } else {
                        filePersistenceService.persistContent(fileDocument.getId(), new ByteArrayInputStream(artwork.getBinaryData()));
                        fileDocument.setContent(new byte[0]);
                    }
                }
            }
            if (audioFileTag instanceof ID3v23Tag tag) {
                String songName;
                String singer;
                String album;
                if (tag.frameMap != null) {
                    if (tag.frameMap.get(SONGNAME_TAG) != null) {
                        // 歌名
                        AbstractID3v2Frame frame = (AbstractID3v2Frame) tag.frameMap.get(SONGNAME_TAG);
                        FrameBodyTIT2 framebodytit2 = (FrameBodyTIT2) frame.getBody();
                        songName = framebodytit2.getText();
                        if (StrUtil.isEmpty(songName)) {
                            songName = "未知歌曲";
                        }
                        music.setSongName(songName);
                    }
                    if (tag.frameMap.get(SINGER_TAG) != null) {
                        // 歌手
                        AbstractID3v2Frame frame = (AbstractID3v2Frame) tag.frameMap.get(SINGER_TAG);
                        FrameBodyTPE1 framebodytpe1 = (FrameBodyTPE1) frame.getBody();
                        singer = framebodytpe1.getText();
                        if (StrUtil.isEmpty(singer)) {
                            singer = "未知歌手";
                        }
                        music.setSinger(singer);
                    }
                    if (tag.frameMap.get(ALBUM_TAG) != null) {
                        // 专辑
                        AbstractID3v2Frame frame = (AbstractID3v2Frame) tag.frameMap.get(ALBUM_TAG);
                        FrameBodyTALB frameBodyTALB = (FrameBodyTALB) frame.getBody();
                        album = frameBodyTALB.getText();
                        if (StrUtil.isEmpty(album)) {
                            album = "未知专辑";
                        }
                        music.setAlbum(album);
                    }
                    if (tag.frameMap.get(COVER_TAG) != null) {
                        // 封面
                        AbstractID3v2Frame frame = (AbstractID3v2Frame) tag.frameMap.get(COVER_TAG);
                        FrameBodyAPIC body4 = (FrameBodyAPIC) frame.getBody();
                        if (dataSourceProperties.getType() == DataSourceType.mongodb) {
                            String Base64 = cn.hutool.core.codec.Base64.encode(body4.getImageData());
                            music.setCoverBase64(Base64);
                        } else {
                            filePersistenceService.persistContent(fileDocument.getId(), new ByteArrayInputStream(body4.getImageData()));
                            fileDocument.setContent(new byte[0]);
                        }
                    }
                }
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
            log.warn(e.getMessage());
        }
        return music;
    }

}
