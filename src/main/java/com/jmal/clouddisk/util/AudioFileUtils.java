package com.jmal.clouddisk.util;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.model.Music;
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
import org.jaudiotagger.tag.id3.framebody.*;

import java.io.File;
import java.io.IOException;

/***
 * 读取音频文件信息工具
 */
public class AudioFileUtils {
    public static Music readAudio(File file){
        Music music = new Music();
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            Tag audioFileTag = audioFile.getTag();
            if(audioFileTag instanceof FlacTag){
                FlacTag tag = (FlacTag) audioFileTag;
                System.out.println(tag.getFirst(FieldKey.TITLE));
                Artwork artwork = tag.getFirstArtwork();
                if(artwork != null){
                    String Base64 = ImgUtil.toBase64(ImgUtil.toImage(artwork.getBinaryData()),artwork.getMimeType());
                    music.setCoverBase64(Base64);
                }
            }
            if(audioFileTag instanceof ID3v23Tag){
                ID3v23Tag tag = (ID3v23Tag) audioFileTag;
                String songName = "";
                String singer = "";
                String album = "";
                if(tag.frameMap != null){
                    if(tag.frameMap.get("TIT2") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("TIT2");//歌名
                        FrameBodyTIT2 frameBodyTIT2 = (FrameBodyTIT2)frame.getBody();
                        songName = frameBodyTIT2.getText();
                        if(StrUtil.isEmpty(songName)){
                            songName = "未知歌曲";
                        }
                        music.setSongName(songName);
                    }
                    if(tag.frameMap.get("TPE1") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("TPE1");//歌手
                        FrameBodyTPE1 frameBodyTPE1 = (FrameBodyTPE1)frame.getBody();
                        singer = frameBodyTPE1.getText();
                        if(StrUtil.isEmpty(singer)){
                            singer = "未知歌手";
                        }
                        music.setSinger(singer);
                    }
                    if(tag.frameMap.get("TALB") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("TALB");//专辑
                        FrameBodyTALB frameBodyTALB = (FrameBodyTALB)frame.getBody();
                        album = frameBodyTALB.getText();
                        if(StrUtil.isEmpty(album)){
                            album = "未知专辑";
                        }
                        music.setAlbum(album);
                    }
                    if(tag.frameMap.get("APIC") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("APIC");//封面
                        FrameBodyAPIC body4 = (FrameBodyAPIC) frame.getBody();
                        String Base64 = cn.hutool.core.codec.Base64.encode(body4.getImageData());
                        music.setCoverBase64(Base64);
                    }
                }
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
            e.printStackTrace();
        }
        return music;
    }

    public static void main(String[] args) {
        Music music = readAudio(new File("/Users/jmal/Music/网易云音乐/庄典 - 千字文~第1章~.mp3"));
        System.out.println(music.toString());
    }
}
