package com.jmal.juc.funcation;

import java.util.function.Predicate;

public class Demo02 {
    public static void main(String[] args) {

//        Predicate<String> predicate = new Predicate<String>() {
//            @Override
//            public boolean test(String str) {
//                return str.isEmpty();
//            }
//        };

        Predicate<String> predicate = String::isEmpty;

        System.out.println(predicate.test("456"));


    }
}
