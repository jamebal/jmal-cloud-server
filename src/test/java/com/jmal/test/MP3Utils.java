package com.jmal.test;

import java.io.File;
import java.io.IOException;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.datatype.Artwork;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.framebody.*;

/**
 * MP3工具类
 * @author zhaoqx
 *
 */
@Slf4j
public class MP3Utils {
    public static void main(String[] args) {
        MP3File mp3File = null;
        try {
            AudioFile audioFile = AudioFileIO.read(new File("/Users/jmal/Music/网易云音乐/Mark Petrie - New Light No Synth.mp3"));
            Tag audioFileTag = audioFile.getTag();
            if(audioFileTag instanceof FlacTag){
                FlacTag tag = (FlacTag) audioFileTag;
                System.out.println(tag.getFirst(FieldKey.TITLE));
                Artwork artwork = tag.getFirstArtwork();
                if(artwork != null){
                    byte[] cover = artwork.getBinaryData();
                    FileUtil.writeBytes(cover,"/Users/jmal/Music/网易云音乐/岑宁儿 - 追光者/cover.png");
                }
            }
            if(audioFileTag instanceof ID3v23Tag){
                ID3v23Tag tag = (ID3v23Tag) audioFileTag;
                String songName = "";
                String singer = "";
                String album = "";
                if(tag != null && tag.frameMap != null){
                    if(tag.frameMap.get("TIT2") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("TIT2");//歌名
                        FrameBodyTIT2 frameBodyTIT2 = (FrameBodyTIT2)frame.getBody();
                        songName = frameBodyTIT2.getText();
                        if(StrUtil.isEmpty(songName)){
                            songName = "未知歌曲";
                        }
                        log.info(songName);
                    }
                    if(tag.frameMap.get("TPE1") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("TPE1");//歌手
                        FrameBodyTPE1 frameBodyTPE1 = (FrameBodyTPE1)frame.getBody();
                        singer = frameBodyTPE1.getText();
                        if(StrUtil.isEmpty(singer)){
                            singer = "未知歌手";
                        }
                        log.info(singer);
                    }
                    if(tag.frameMap.get("TALB") != null){
                        AbstractID3v2Frame frame = (AbstractID3v2Frame)tag.frameMap.get("TALB");//歌手
                        FrameBodyTALB frameBodyTALB = (FrameBodyTALB)frame.getBody();
                        album = frameBodyTALB.getText();
                        if(StrUtil.isEmpty(album)){
                            album = "未知专辑";
                        }
                        log.info(album);
                    }
                }
                // 封面
                AbstractID3v2Frame frame4 = (AbstractID3v2Frame) tag.getFrame("APIC");
                FrameBodyAPIC body4 = (FrameBodyAPIC) frame4.getBody();
                byte[] cover = body4.getImageData();
                FileUtil.writeBytes(cover,"/Users/jmal/Music/网易云音乐/岑宁儿 - 追光者/cover.png");
                //
                AbstractID3v2Frame frame5 = (AbstractID3v2Frame) tag.getFrame("COMM");
                FrameBodyCOMM body5 = (FrameBodyCOMM) frame5.getBody();
                System.out.println(body5.getDescription());
            }
        } catch (CannotReadException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TagException e) {
            e.printStackTrace();
        } catch (ReadOnlyFileException e) {
            e.printStackTrace();
        } catch (InvalidAudioFrameException e) {
            e.printStackTrace();
        }
        //
//        System.out.println(tag.toString());
    }

    //去除不必要的字符串
    public static String reg(String input) {
        return input;
    }

//    /**
//     * 解析MP3获取歌名，歌手，专辑
//     * @param music 音乐Bean
//     * @return
//     */
//    public static Music getSongInfo(Music music) {
//        try {
//            MP3File mp3File = (MP3File) AudioFileIO.read(new File("/Users/jmal/Music/网易云音乐/岑宁儿 - 追光者.mp3"));
//            AbstractID3v2Tag tag = mp3File.getID3v2Tag();
//            String songName = "";
//            String singer = "";
//            String author = "";
//            //MP3AudioHeader audioHeader = (MP3AudioHeader) mp3File.getAudioHeader();
//            if(mp3File.getID3v2Tag() != null && mp3File.getID3v2Tag().frameMap != null){
//                if(tag.frameMap.get("TIT2") != null){
//                    songName = tag.frameMap.get("TIT2").toString();//歌名
//                    if(!StrUtil.isNotBlank(songName)){
//                        songName = "未知歌曲";
//                    }
//                    music.setTitle(reg(songName));
//                }
//                if(tag.frameMap.get("TPE1") != null){
//                    singer = mp3File.getID3v2Tag().frameMap.get("TPE1").toString();//歌手
//                    if(!StrUtil.isNotBlank(singer)){
//                        singer = "未知歌手";
//                    }
//                    music.setSinger(reg(singer));
//                }
//                if(tag.frameMap.get("TALB") != null){
//                    author = mp3File.getID3v2Tag().frameMap.get("TALB").toString();//专辑
//                    music.setAlbum(reg(author));
//                }
//            }
//            //int duration = audioHeader.getTrackLength();//时长
//        } catch (Exception e) {
//            log.error("MP3Utils:读取MP3信息失败！");
//        }
//        return music;
//    }
//
//
//    //去除不必要的字符串
//    public static String reg(String input) {
//        return input.substring(input.indexOf('"') + 1, input.lastIndexOf('"'));
//    }
//
//    /**
//     * 获取MP3封面图片
//     * @param mp3File
//     * @return
//     * @throws InvalidAudioFrameException
//     * @throws ReadOnlyFileException
//     * @throws TagException
//     * @throws IOException
//     * @throws CannotReadException
//     */
//    public static byte[] getMP3Image(Music music) {
//        byte[] imageData = null;
//        MP3File mp3File;
//        try {
//            mp3File = (MP3File) AudioFileIO.read(new File(PathKit.getWebRootPath() + music.getMusicUrl()));
//            AbstractID3v2Tag tag = mp3File.getID3v2Tag();
//            AbstractID3v2Frame frame = (AbstractID3v2Frame) tag.getFrame("APIC");
//            FrameBodyAPIC body = (FrameBodyAPIC) frame.getBody();
//            imageData = body.getImageData();
//        } catch (Exception e) {
//            log.error("MP3Utils:读取MP3封面失败！");
//            return null;
//        }
//        return imageData;
//    }
//
//    /**
//     * 获取mp3图片并将其保存至指定路径下
//     * 如果没有读取到图片 ，则返回"/static/music/images/defulate.jpg"
//     * @param music mp3文件对象
//     * @param mp3ImageSavePath mp3图片保存位置（默认mp3ImageSavePath +"\" mp3File文件名 +".jpg" ）
//     * @param cover 是否覆盖已有图片
//     * @return 生成图片路径
//     */
//    public static String saveMP3Image(Music music, String mp3ImageSavePath, boolean cover) {
//        //生成mp3图片路径
//        //PathKit.getWebRootPath() + music.getMusicUrl() 路径前缀，修改成自己的url前缀
//        File file = new File(PathKit.getWebRootPath() + music.getMusicUrl());
//        String mp3FileLabel = file.getName();
//        String mp3ImageFullPath = mp3ImageSavePath + ("\\" + mp3FileLabel + ".jpg");
//
//        //若为非覆盖模式，图片存在则直接返回（不再创建）
//        if( !cover ) {
//            File tempFile = new File(mp3ImageFullPath) ;
//            if(tempFile.exists()) {
//                return mp3ImageFullPath;
//            }
//        }
//
//        //生成mp3存放目录
//        File saveDirectory = new File(mp3ImageSavePath);
//        saveDirectory.mkdirs();
//
//        //获取mp3图片
//        byte imageData[];
//        imageData = getMP3Image(music);
//        if(imageData == null){
//            log.error("MP3Utils:读取MP3封面失败！");
//            //获取失败，返回默认图片路径
//            return "/static/music/images/defulate.jpg";
//        }
//
//        //若图片不存在，则直接返回null
//        if (null == imageData || imageData.length == 0) {
//            return null;
//        }
//        //保存mp3图片文件
//        FileOutputStream fos = null;
//        try {
//            fos = new FileOutputStream(mp3ImageFullPath);
//            fos.write(imageData);
//            fos.close();
//        } catch(Exception e) {
//            log.error("MP3Utils:保存读取mp3图片文件失败！");
//        }
//        return mp3ImageFullPath.substring(PathKit.getWebRootPath().length(), mp3ImageFullPath.length());
//    }
}
