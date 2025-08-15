package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

/**
 * @author jmal
 */
@Data
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
}
