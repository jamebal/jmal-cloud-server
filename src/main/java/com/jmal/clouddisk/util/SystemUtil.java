package com.jmal.clouddisk.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.system.oshi.CpuInfo;
import cn.hutool.system.oshi.OshiUtil;
import lombok.extern.slf4j.Slf4j;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;

import java.io.File;

/**
 * @author jmal
 * @Description 系统信息
 * @date 2022/4/13 14:13
 */
@Slf4j
public class SystemUtil {

    public static void main(String[] args) throws Exception {
        log.info("CPU使用率: {}%", getCpuUsed());
        log.info("内存使用率: {}%", getMemoryUsed());
        log.info("磁盘使用率: {}%", getDiskUsed());
        log.info("磁盘可以用空间: {}G", getFreeSpace());
        for (HWDiskStore diskStore : OshiUtil.getHardware().getDiskStores()) {
            Console.log(diskStore.getName(), diskStore.getModel(), diskStore.getSize()/1024/1024/1024+"G", diskStore.getWrites(), diskStore.getReads(), diskStore.getReadBytes());
        }
    }

    /**
     * 获取CPU使用率
     */
    public static double getCpuUsed(){
        CpuInfo cpuInfo = OshiUtil.getCpuInfo();
        return cpuInfo.getUsed();
    }

    /**
     * 获取内存使用率
     */
    public static double getMemoryUsed(){
        GlobalMemory globalMemory = OshiUtil.getHardware().getMemory();
        return getMemoryUsed(globalMemory);
    }

    /**
     * 获取内存使用率
     * @param globalMemory GlobalMemory
     * @return 获取内存使用率
     */
    private static double getMemoryUsed(GlobalMemory globalMemory){
        return NumberUtil.round((1 - Convert.toDouble(globalMemory.getAvailable()) / globalMemory.getTotal()) * 100, 2).doubleValue();
    }

    /**
     * 获取硬盘使用率
     */
    public static double getDiskUsed(){
        File win = new File("/");
        if (win.exists()) {
            long total = win.getTotalSpace();
            long freeSpace = win.getFreeSpace();
            return NumberUtil.round((1 - freeSpace/Convert.toDouble(total)) * 100,2).doubleValue();
        }
        return 0;
    }

    /**
     * 获取硬盘可用空间(Gb)
     */
    public static long getFreeSpace(){
        File win = new File("/");
        if (win.exists()) {
            long freeSpace = win.getFreeSpace();
            return freeSpace/1024/1024/1024;
        }
        return 0;
    }
}
