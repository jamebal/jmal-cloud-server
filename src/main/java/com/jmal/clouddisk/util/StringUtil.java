package com.jmal.clouddisk.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;


import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description StringUtil
 * @Date 2021/4/29 2:54 下午
 */
public class StringUtil {

    /**
     * Lucene query 中需要被转义的关键字
     */
    public final static Set<Character> RE_KEYS = CollUtil.newHashSet('&', '(', ')', '!', '*', '+', '"', '~', '-', '[', ']', '?', ':', '\\', '^', '{', '}', '|', '/');

    /**
     * 转义字符串，将正则的关键字转义
     *
     * @param content 文本
     * @return 转义后的文本
     */
    public static String escape(CharSequence content) {
        if (CharSequenceUtil.isBlank(content)) {
            return StrUtil.str(content);
        }

        final StringBuilder builder = new StringBuilder();
        int len = content.length();
        char current;
        for (int i = 0; i < len; i++) {
            current = content.charAt(i);
            if (RE_KEYS.contains(current)) {
                builder.append('\\');
            }
            builder.append(current);
        }
        return builder.toString();
    }

    /**
     * 文本中是否含有中文
     */
    public static boolean isContainChinese(String str) {
        if (CharSequenceUtil.isBlank(str)) {
            return false;
        }
        Pattern p = Pattern.compile("[\u4e00-\u9fa5]");
        Matcher m = p.matcher(str);
        return m.find();
    }

    /**
     * 是否为短字符
     * 英文 <= 2
     * 中文 <= 1
     */
    public static boolean isShortStr(String str) {
        if (CharSequenceUtil.isBlank(str)) {
            return true;
        }
        if (isContainChinese(str)) {
            return str.length() <= 1;
        } else {
            return str.length() <= 2;
        }
    }
}
