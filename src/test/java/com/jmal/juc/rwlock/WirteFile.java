package com.jmal.juc.rwlock;

import cn.hutool.core.io.FileUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.impl.FileServiceImpl;
import sun.security.krb5.internal.Ticket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * @Description jmal 模拟合并文件的操作
 * @Author jmal
 * @Date 2020-06-10 09:26
 */
public class WirteFile {

    public static void main(String[] args) {
        long stime = System.currentTimeMillis();
        TicketFile ticket = new TicketFile(stime);
        File parent = Paths.get("/Users/jmal/temp/filetest/rootpath/ugyuvgbhnouvghjbnk/jmal/").toFile();
        // 排除文件，只要目录
        File[] parentFileArray = parent.listFiles(pathName -> pathName.isDirectory());
        for (File parentFile : parentFileArray) {
            new Thread(()->{
                String md5 = parentFile.getName();
                File f = Paths.get("/Users/jmal/temp/filetest/rootpath/ugyuvgbhnouvghjbnk/jmal/",md5).toFile();
                // 排除目录，只要文件
                File[] fileArray = f.listFiles(pathName -> !pathName.isDirectory());
                // 转成集合，便于排序
                List<File> fileList = new ArrayList<>(Arrays.asList(Objects.requireNonNull(fileArray)));
                // 从小到大排序
//                fileList.sort(WirteFile::compare);
                for (File file : fileList) {
                    new Thread(()->{
                        UploadApiParam upload = new UploadApiParam();
                        int chunk = Integer.parseInt(file.getName());
                        upload.setChunkNumber(chunk);
                        upload.setIdentifier(md5);
                        upload.setUsername("jmal");
                        System.out.println(md5+"-"+chunk+"开启任务");
                        ticket.appendChunkFile(upload);
                    }).start();
                }
            }, parentFile.getName()).start();
        }
        System.out.println("加锁耗时:"+(System.currentTimeMillis()-stime)+"ms");
    }

    private static int compare(File o1, File o2) {
        if (Integer.parseInt(o1.getName()) < Integer.parseInt(o2.getName())) {
            return -1;
        }
        return 1;
    }
}

class TicketFile {

    long stime;

    public TicketFile(long stime){
        this.stime = stime;
    }

    /***
     * 以写入的分片索引
     */
    public static Cache<String, CopyOnWriteArrayList<Integer>> writtenCache = Caffeine.newBuilder().build();

    /***
     * 未写入(以上传)的分片索引
     */
    public static Cache<String, CopyOnWriteArrayList<Integer>> unWrittenCache = Caffeine.newBuilder().build();

    public static Cache<String, Lock> lockCace = Caffeine.newBuilder().build();

    // 读写锁
    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /***
     * 追加分片
     * @param upload
     */
    public void appendChunkFile(UploadApiParam upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        // 未写入的分片
        CopyOnWriteArrayList<Integer> unWrittenChunks = unWrittenCache.get(md5, key ->  new CopyOnWriteArrayList<>());
        if (!unWrittenChunks.contains(chunkNumber)) {
            unWrittenChunks.add(chunkNumber);
            unWrittenCache.put(md5, unWrittenChunks);
        }

        // 以写入的分片
        CopyOnWriteArrayList<Integer> writtenChunks = writtenCache.get(md5, key ->  new CopyOnWriteArrayList<>());
        Path filePath = Paths.get("/Users/jmal/temp/filetest/rootpath/ugyuvgbhnouvghjbnk/jmal",md5+".mp4");

        Lock lock = lockCace.get(md5,key -> new ReentrantLock());
        lock.lock();
        try{
            if(Files.exists(filePath) && writtenChunks.size() > 0){
                // 继续追加
                for (int unWrittenChunk : unWrittenChunks) {
                    appenFile(upload, unWrittenChunks, writtenChunks);
                }
            }else{
                // 首次写入
                if(Files.exists(filePath)){
                    try {
                        Files.delete(filePath);
                    } catch (IOException e) {
                        throw new CommonException(ExceptionType.FAIL_MERGA_FILE);
                    }
                }
                appenFile(upload, unWrittenChunks, writtenChunks);
            }
        } catch (Exception e){
            throw new CommonException(-1,e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void appenFile(UploadApiParam upload, CopyOnWriteArrayList<Integer> unWrittenChunks, CopyOnWriteArrayList<Integer> writtenChunks) {
        // 需要继续追加分片索引
        int chunk = 1;
        if(writtenChunks.size() > 0){
            chunk = writtenChunks.get(writtenChunks.size()-1) +1;
        }
        if(!unWrittenChunks.contains(chunk)){
            return;
        }
        String md5 = upload.getIdentifier();
        // 分片文件
        File file = Paths.get("/Users/jmal/temp/filetest/rootpath/ugyuvgbhnouvghjbnk/jmal",md5,chunk+"").toFile();
        // 目标文件
        File outputFile = Paths.get("/Users/jmal/temp/filetest/rootpath/ugyuvgbhnouvghjbnk/jmal",md5+".mp4").toFile();
        System.out.println(md5+"-"+chunk+"正在写入");
        long postion = outputFile.length();
        long count = file.length();
        try(FileOutputStream fileOutputStream = new FileOutputStream(outputFile,true);
            FileChannel outChannel = fileOutputStream.getChannel()){
            try(FileInputStream fileInputStream = new FileInputStream(file);
                FileChannel inChannel = fileInputStream.getChannel()){
                ByteBuffer byteBuffer = ByteBuffer.wrap(FileUtil.readBytes(file));
                outChannel.write(byteBuffer,postion);
                writtenChunks.add(chunk);
                writtenCache.put(md5, writtenChunks);
                unWrittenChunks.remove(unWrittenChunks.indexOf(chunk));
                unWrittenCache.put(md5,unWrittenChunks);
            }
            System.out.println(md5+"-"+chunk+"写入完成,unWrittenChunks:"+unWrittenChunks.size()+",writtenChunks:"+writtenChunks.size());
            if(unWrittenChunks.size() == 0){
                System.out.println("加锁耗时:"+(System.currentTimeMillis()-stime)+"ms");
            }
        }catch (IOException e){
            throw new CommonException(-1, "合并文件失败");
        }
    }
}
