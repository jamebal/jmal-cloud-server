package com.jmal.clouddisk;
import cn.hutool.core.lang.Console;

/**
 * @author jmal
 * @Description Test
 * @Date 2021/1/18 10:06 上午
 */
public class Test {

    /**
     * 十六进制字符的输出的小写字符数组
     */
    private static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * 两个或三个十六进制字符转换后的字符
     */
    private static final char[] CODES = {'0','1','2','3','4','5','6','7','8','9',
            'A','B','C','D','E','F','G','H','I','J',
            'K','L','M','N','O','P','Q','R','S','T',
            'U','V','W','X','Y','Z','a','b','c','d',
            'e','f','g','h','i','j','k','l','m','n',
            'o','p','q','r','s','t','u','v','w','x',
            'y','z','_'};
    /**
     * 单元测试
     * 运行： java RandomStr 4  (生成长度为4的字符串)
     */
    public static void main(String[] args){

        String str = "5fd1b60461f00c5be3249fff";
        long stime = System.currentTimeMillis();

        StringBuilder out = new StringBuilder();

        for (int i = 3; i < str.length() + 2; i = i + 3) {
            Console.log(str.charAt(i-3), indexOf(str.charAt(i-3)));
            Console.log(str.charAt(i-2), indexOf(str.charAt(i-2)));
            Console.log(str.charAt(i-1), indexOf(str.charAt(i-1)));
            int index = indexOf(str.charAt(i-3)) + indexOf(str.charAt(i-2)) + indexOf(str.charAt(i-1));
            out.append(CODES[index]);
        }

        Console.log(out.toString(), System.currentTimeMillis() - stime);
    }

    private static int indexOf(char hex){
        for (int i = 0; i < DIGITS_LOWER.length; i++) {
            if(DIGITS_LOWER[i] == hex){
                return i;
            }
        }
        return -1;
    }

    /**
     * 返回随机字符串，同时包含数字、大小写字母
     * @param len 字符串长度，不能小于3
     * @return String 随机字符串
     */
    public static String randomStr(int len){
        if(len < 3){
            throw new IllegalArgumentException("字符串长度不能小于3");
        }
        //数组，用于存放随机字符
        char[] chArr = new char[len];
        //为了保证必须包含数字、大小写字母
        chArr[0] = (char)('0' + StdRandom.uniform(0,10));
        chArr[1] = (char)('A' + StdRandom.uniform(0,26));
        chArr[2] = (char)('a' + StdRandom.uniform(0,26));


        char[] codes = {'0','1','2','3','4','5','6','7','8','9',
                'A','B','C','D','E','F','G','H','I','J',
                'K','L','M','N','O','P','Q','R','S','T',
                'U','V','W','X','Y','Z','a','b','c','d',
                'e','f','g','h','i','j','k','l','m','n',
                'o','p','q','r','s','t','u','v','w','x',
                'y','z','_'};
        //charArr[3..len-1]随机生成codes中的字符
        for(int i = 3; i < len; i++){
            chArr[i] = codes[StdRandom.uniform(0,codes.length)];
        }

        //将数组chArr随机排序
        for(int i = 0; i < len; i++){
            int r = i + StdRandom.uniform(len - i);
            char temp = chArr[i];
            chArr[i] = chArr[r];
            chArr[r] = temp;
        }

        return new String(chArr);
    }
}
