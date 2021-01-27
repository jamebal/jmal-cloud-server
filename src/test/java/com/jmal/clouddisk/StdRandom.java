package com.jmal.clouddisk;

import java.util.Random;

public final class StdRandom {

    //随机数生成器
    private static Random random;
    //种子值
    private static long seed;

    //静态代码块，初始化种子值及随机数生成器
    static {
        seed = System.currentTimeMillis();
        random = new Random(seed);
    }

    //私有构造函数，禁止实例化
    private StdRandom() {}

    /**
     * 设置种子值
     * @param s 随机数生成器的种子值
     */
    public static void setSeed(long s){
        seed = s;
        random = new Random(seed);
    }

    /**
     * 获取种子值
     * @return long 随机数生成器的种子值
     */
    public static long getSeed(){
        return seed;
    }

    /**
     * 随机返回0到1之间的实数 [0,1)
     * @return double 随机数
     */
    public static double uniform(){
        return random.nextDouble();
    }

    /**
     * 随机返回0到N-1之间的整数 [0,N)
     * @param N 上限
     * @return int 随机数
     */
    public static int uniform(int N){
        return random.nextInt(N);
    }

    /**
     * 随机返回0到1之间的实数 [0,1)
     * @return double 随机数
     */
    public static double random(){
        return uniform();
    }

    /**
     * 随机返回a到b-1之间的整数 [a,b)
     * @param a 下限
     * @param b 上限
     * @return int 随机数
     */
    public static int uniform(int a,int b){
        return a + uniform(b - a);
    }

    /**
     * 随机返回a到b之间的实数
     * @param a 下限
     * @param b 上限
     * @return double 随机数
     */
    public static double uniform(double a,double b){
        return a + uniform() * (b - a);
    }
}