package com.jmal.clouddisk.model;

import lombok.Data;

/**
 * @author jmal
 */
@Data
public class Music {
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
}
