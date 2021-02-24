package com.jmal.clouddisk.ip2region;

import cn.hutool.core.lang.Console;
import org.lionsoul.ip2region.*;

import java.io.*;

/**
 * @author jmal
 * @Description ip2region test
 * @Date 2021/2/23 1:44 下午
 */
public class Test {
    public static void main(String[] args) {
        File file = new File("/Users/jmal/studio/myProject/github/ip2region/data/ip2region.db");
        if (!file.exists()) {
            System.out.println("Error: Invalid ip2region.db file");
            return;
        }
        String algoName = "B-tree";
        try {
            System.out.println("initializing "+algoName+" ... ");
            DbConfig config = new DbConfig();
            String path = Thread.currentThread().getContextClassLoader().getResource("db/ip2region.db").getPath();
            Console.log("path: {}", path);
            DbSearcher searcher = new DbSearcher(config, "/Users/jmal/studio/myProject/github/jmal-cloud-server/target/classes/db/ip2region.db");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("+----------------------------------+");
            System.out.println("| ip2region test shell             |");
            System.out.println("| Author: chenxin619315@gmail.com  |");
            System.out.println("| Type 'quit' to exit program      |");
            System.out.println("+----------------------------------+");

            double sTime = 0, cTime = 0;
            String line = null;
            DataBlock dataBlock = null;
            while ( true ) {
                System.out.print("ip2region>> ");
                line = reader.readLine().trim();
                if ( line.length() < 2 ) continue;
                if ( line.equalsIgnoreCase("quit") ) break;
                if (!Util.isIpAddress(line)) {
                    System.out.println("Error: Invalid ip address");
                    continue;
                }

                sTime = System.nanoTime();
                dataBlock = searcher.memorySearch(line);
                cTime = (System.nanoTime() - sTime) / 1000000;
                System.out.printf("%s in %.5f millseconds\n", dataBlock, cTime);
            }

            reader.close();
            searcher.close();
            System.out.println("+--Bye");
        } catch (IOException | DbMakerConfigException | SecurityException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }
}
