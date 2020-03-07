package com.jmal.juc.funcation;

import java.util.function.Supplier;

public class Demo04 {
    public static void main(String[] args) {

//        Supplier<String> supplier = new Supplier<String>() {
//            // 语法糖
//            @Override
//            public String get() {
//                return "《hello，spring》";
//            }
//        };
        Supplier<String> supplier = ()-> "《hello，spring》";
        System.out.println(supplier.get());
    }
}
