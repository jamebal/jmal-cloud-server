package com.jmal.clouddisk.util;

/**
 * @author jmal
 * @Description StringUtil
 * @Date 2021/4/29 2:54 下午
 */
public class StringUtil {

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':' || c == '^' || c == '[' || c == ']' || c == '"' || c == '{' || c == '}' || c == '~' || c == '*' || c == '?' || c == '#' || c == '|' || c == '&' || c == '/') {
                sb.append('\\');
            }

            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * 移除字符串中的空字符 (\u0000)
     *
     * @param input 输入字符串
     * @return 清理后的字符串，null 安全
     */
    public static String removeNullChar(String input) {
        return input == null ? null : input.replace("\u0000", "");
    }

}
