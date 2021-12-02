package com.jmal.clouddisk.security;


public class Test {
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 6000;
        long index = 0;
        while(true){
            double x = Math.sqrt(index);
            long now = System.currentTimeMillis();
            if (now > endTime) {
                break;
            }
            index++;
        }
        System.out.println("6秒计算次数:" + index);
    }
}
