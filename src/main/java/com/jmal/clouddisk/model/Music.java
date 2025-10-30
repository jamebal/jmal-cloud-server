package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.util.StringUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author jmal
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Music implements Reflective {
    /***
     * 歌名
     */
    String songName;
    /***
     * 歌手
     */
    String singer;
    /***
     * 专辑
     */
    String album;
    /***
     * 封面
     */
    String coverBase64;

    public void setSinger(String singer) {
        this.singer = StringUtil.removeNullChar(singer);
    }

    public void setAlbum(String album) {
        this.album = StringUtil.removeNullChar(album);
    }

    public void setSongName(String songName) {
        this.songName = StringUtil.removeNullChar(songName);
    }

    public Music(MusicInfo music) {
        if (music != null) {
            this.songName = music.getSongName();
            this.singer = music.getSinger();
            this.album = music.getAlbum();
        }
    }
}
