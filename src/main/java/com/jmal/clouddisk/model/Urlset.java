package com.jmal.clouddisk.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description sitemap xml
 * @Date 2021/02/25 4:26 下午
 */

@JacksonXmlRootElement(localName = "urlset")
@Data
public class Urlset implements Reflective {

    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Url> url = new ArrayList<>();

    @JacksonXmlRootElement(localName = "url")
    @Data
    public static class Url {
        /***
         * 必填标签,这是具体某一个链接的定义入口，每一条数据都要用<url>和</url>包含在里面，这是必须的
         */
        @JacksonXmlProperty(localName = "loc")
        private String loc;
        /***
         * 可以不提交该标签,用来指定该链接的最后更新时间
         */
        @JacksonXmlProperty(localName = "lastmod")
        private String lastmod;
        /***
         * 可以不提交该标签,用这个标签告诉此链接可能会出现的更新频率
         */
        @JacksonXmlProperty(localName = "changefreq")
        private String changefreq;
        /***
         * 可以不提交该标签,用来指定此链接相对于其他链接的优先权比值，此值定于0.0-1.0之间
         */
        @JacksonXmlProperty(localName = "priority")
        private String priority;
    }
}
