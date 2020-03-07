package com.jmal.juc.rwlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
独占锁（写锁）：一次只能被一个线程占有
共享锁（读锁）：该锁可以被多个线程占有！
 */
public class ReadWriteLockDemo {
    public static void main(String[] args) {
//        // 不加锁
//        MyCache myCache = new MyCache();
        // 枷锁
        MyCacheLock myCache = new MyCacheLock();
        // 模拟线程
        // 写
        for (int i = 1; i <= 5; i++) {
            final int tempInt = i;
            new Thread(()-> myCache.put(tempInt+"",tempInt+""),String.valueOf(i)).start();
        }

        // 读
        for (int i = 1; i <= 5; i++) {
            final int tempInt = i;
            new Thread(()-> myCache.get(tempInt+""),String.valueOf(i)).start();
        }
    }
}

// 不加锁 读、写
class MyCache {

    private volatile Map<String, Object> map = new HashMap<>();

    // 读 ： 可以被多个线程同时读
    private void get(String key) {
        System.out.println(Thread.currentThread().getName() + "读取" + key);
        Object o = map.get(key);
        System.out.println(Thread.currentThread().getName() + "读取结果:" + o);
    }

    // 写 ：应该是保证原子性 , 不应该被打扰
    private void put(String key, Object value) {
        System.out.println(Thread.currentThread().getName() + "写入" + key);
        map.put(key, value);
        System.out.println(Thread.currentThread().getName() + "写入ok");
    }
}

// 加锁操作： 读写锁
class MyCacheLock{

    private volatile Map<String,Object> map = new HashMap<>();
    // 读写锁
    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    // 读 ： 可以被多个线程同时读
    public void get(String key){
        // 这些锁一定要匹配，否则就可能导致死锁！
        readWriteLock.readLock().lock(); // 多个线程同时持有
        try {
            System.out.println(Thread.currentThread().getName()+"读取" + key);
            Object o = map.get(key);
            System.out.println(Thread.currentThread().getName()+"读取结果:"+o);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    // 写 ：应该是保证原子性 , 不应该被打扰
    public void put(String key,Object value){

        readWriteLock.writeLock().lock(); // 只能被一个线程占用
        try {
            System.out.println(Thread.currentThread().getName()+"写入" + key);
            map.put(key,value);
            System.out.println(Thread.currentThread().getName()+"写入ok" );
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

}
