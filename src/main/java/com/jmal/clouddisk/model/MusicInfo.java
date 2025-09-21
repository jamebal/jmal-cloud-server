package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
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
public class MusicInfo implements Reflective {
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

    public MusicInfo(Music music) {
        this.songName = music.getSongName();
        this.singer = music.getSinger();
        this.album = music.getAlbum();
    }
}
