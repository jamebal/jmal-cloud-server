package com.jmal.juc.pool;


import java.util.concurrent.*;

public class ThreadPoolDemo01 {
    public static void main(String[] args) {
        ExecutorService threadPool = new ThreadPoolExecutor(
                2,
                5,
                3L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(3),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        try {
            // 线程池的使用方式！
            for (int i = 0; i < 100; i++) {
                threadPool.execute(()-> System.out.println(Thread.currentThread().getName() + " ok"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 使用完毕后需要关闭！
            threadPool.shutdown();
        }

    }
}
