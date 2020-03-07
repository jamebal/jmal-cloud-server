package com.jmal.juc.queue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class BlockingDemo {
    public static void main(String[] args) throws InterruptedException {
        // 参数，队列的大小
        ArrayBlockingQueue blockingQueue = new ArrayBlockingQueue<>(3);
        // java.lang.IllegalStateException: Queue full
        blockingQueue.add("a");
        blockingQueue.add("b");
        blockingQueue.add("c");
        System.out.println(blockingQueue.element());

        //blockingQueue.add("d"); // 报错、抛弃不报错、一直等待、超时等待！

//        System.out.println(blockingQueue.offer("a"));
//        System.out.println(blockingQueue.offer("b"));
//        System.out.println(blockingQueue.offer("c"));
//        System.out.println(blockingQueue.offer("d",3L,TimeUnit.SECONDS)); // 尝试等待3秒，就会失败！返回false

//        blockingQueue.put("a");
//        blockingQueue.put("b");
//        blockingQueue.put("c");
//        System.out.println("准备放入第四个元素");
//        blockingQueue.put("d"); // 队列满了，一直等，并且会阻塞！

        System.out.println("========================");

        System.out.println(blockingQueue.take());
        System.out.println(blockingQueue.take());
        System.out.println(blockingQueue.take());
        // System.out.println(blockingQueue.take()); // 队列空了，一直等，并且会阻塞！

    }
}
