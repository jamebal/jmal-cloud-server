package com.jmal.clouddisk.ip2region;

import org.junit.Test;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description ip2region test
 * @Date 2021/2/23 1:44 下午
 */
@SpringBootTest
public class Ip2regionTest {
    private static final String dbPath = "/Users/jmal/studio/myProject/github/jmal-cloud-server/src/main/resources/db/ip2region.xdb";

    @Test
    public void searchFile() throws IOException {
        Searcher searcher;
        try {
            searcher = Searcher.newWithFileOnly(dbPath);
        } catch (IOException e) {
            System.out.printf("failed to create searcher with `%s`: %s\n", dbPath, e);
            return;
        }

        // 2、查询
        String ip = "1.2.3.4";
        try {
            long sTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = TimeUnit.NANOSECONDS.toMicros((long) (System.nanoTime() - sTime));
            int memory = 0 / 1024 /1024;
            System.out.printf("{   完全基于文件的查询: region: %s, ioCount: %d, memory: %d MB, took: %d μs}\n", region, searcher.getIOCount(), memory, cost);
        } catch (Exception e) {
            System.out.printf("failed to search(%s): %s\n", ip, e);
        }

        // 3、关闭资源
        searcher.close();

        // 备注：并发使用，每个线程需要创建一个独立的 searcher 对象单独使用。
    }

    @Test
    public void vectorIndexTest() throws IOException {
        // 1、从 dbPath 中预先加载 VectorIndex 缓存，并且把这个得到的数据作为全局变量，后续反复使用。
        byte[] vIndex;
        try {
            vIndex = Searcher.loadVectorIndexFromFile(dbPath);
        } catch (Exception e) {
            System.out.printf("failed to load vector index from `%s`: %s\n", dbPath, e);
            return;
        }
        // 2、使用全局的 vIndex 创建带 VectorIndex 缓存的查询对象。
        Searcher searcher;
        try {
            searcher = Searcher.newWithVectorIndex(dbPath, vIndex);
        } catch (Exception e) {
            System.out.printf("failed to create vectorIndex cached searcher with `%s`: %s\n", dbPath, e);
            return;
        }
        String ip = "1.2.3.4";
        // 3、查询
        try {
            long sTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = TimeUnit.NANOSECONDS.toMicros((long) (System.nanoTime() - sTime));
            int memory = vIndex.length / 1024;
            System.out.printf("{缓存VectorIndex索引: region: %s, ioCount: %d, memory: %d KB, took: %d μs}\n", region, searcher.getIOCount(), memory, cost);
        } catch (Exception e) {
            System.out.printf("failed to search(%s): %s\n", ip, e);
        }
        // 4、关闭资源
        searcher.close();
    }

    @Test
    public void cacheAllTest() throws IOException {
        // 1、从 dbPath 加载整个 xdb 到内存。
        byte[] cBuff;
        try {
            cBuff = Searcher.loadContentFromFile(dbPath);
        } catch (Exception e) {
            System.out.printf("failed to load content from `%s`: %s\n", dbPath, e);
            return;
        }
        // 2、使用上述的 cBuff 创建一个完全基于内存的查询对象。
        Searcher searcher;
        try {
            searcher = Searcher.newWithBuffer(cBuff);
        } catch (Exception e) {
            System.out.printf("failed to create content cached searcher: %s\n", e);
            return;
        }
        // 3、查询
        String ip = "1.2.3.4";
        try {
            long sTime = System.nanoTime();
            String region = searcher.search(ip);
            long cost = TimeUnit.NANOSECONDS.toMicros((long) (System.nanoTime() - sTime));
            int memory = cBuff.length / 1024 /1024;
            System.out.printf("{     缓存整个xdb数据: region: %s, ioCount: %d, memory: %d MB, took: %d μs}\n", region, searcher.getIOCount(), memory, cost);
        } catch (Exception e) {
            System.out.printf("failed to search(%s): %s\n", ip, e);
        }

        // 4、关闭资源 - 该 searcher 对象可以安全用于并发，等整个服务关闭的时候再关闭 searcher
        // searcher.close();

        // 备注：并发使用，用整个 xdb 数据缓存创建的查询对象可以安全的用于并发，也就是你可以把这个 searcher 对象做成全局对象去跨线程访问.
    }
}
