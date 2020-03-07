package com.jmal.juc.future;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureDemo {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // 没有返回值,好比多线程，功能更强大！
//        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
//            try {
//                TimeUnit.SECONDS.sleep(2);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println(Thread.currentThread().getName() + "没有返回值！");
//        });
//        System.out.println("111111");
//        completableFuture.get();

        // 有返回值
        // 任务
        CompletableFuture<Integer> uCompletableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println(Thread.currentThread().getName()+"=>supplyAsync!");
            int i = 10/0;
            return 1024;
        });

        System.out.println(uCompletableFuture.whenComplete((t, u) -> { // 成功
            System.out.println("t=>" + t); // 正确结果
            System.out.println("u=>" + u); // 错误信息
        }).exceptionally(e -> { // 失败，如果错误就返回错误的结果！
            System.out.println("e:" + e.getMessage());
            return 500;
        }).get());

    }
}
