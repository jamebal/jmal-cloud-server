package com.jmal.juc.funcation;

import java.util.function.Function;

public class Demo01 {
    public static void main(String[] args) {
        //
//        Function<String,Integer> function = new Function<String,Integer>() {
//            @Override
//            public Integer apply(String str) {
//                return str.length();
//            }
//        };

        // (参数)->{方法体}
        Function<String,Integer> function = String::length;
        System.out.println(function.apply("a45645646"));
    }
}
