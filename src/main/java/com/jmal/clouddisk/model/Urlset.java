package com.jmal.clouddisk.model;

import lombok.Data;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description sitemap xml
 * @Date 2021/02/25 4:26 下午
 */

@XmlRootElement
@Data
public class Urlset {

    private List<Url> url = new ArrayList<>();

    @XmlRootElement
    @Data
    public static class Url {
        /***
         * 必填标签,这是具体某一个链接的定义入口，每一条数据都要用<url>和</url>包含在里面，这是必须的
         */
        private String loc;
        /***
         * 可以不提交该标签,用来指定该链接的最后更新时间
         */
        private String lastmod;
        /***
         * 可以不提交该标签,用这个标签告诉此链接可能会出现的更新频率
         */
        private String changefreq;
        /***
         * 可以不提交该标签,用来指定此链接相对于其他链接的优先权比值，此值定于0.0-1.0之间
         */
        private String priority;
    }
}