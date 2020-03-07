package com.jmal.juc.funcation;

import java.util.function.Consumer;

public class Demo03 {
    public static void main(String[] args) {

        Consumer<String> consumer = System.out::println;

//        Consumer<String> consumer = new Consumer<String>() {
//            @Override
//            public void accept(String s) {
//                System.out.println(s);
//            }
//        };

        consumer.accept("hello");

    }
}
