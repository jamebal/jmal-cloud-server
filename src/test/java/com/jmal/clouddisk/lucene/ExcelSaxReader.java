package com.jmal.clouddisk.lucene;

import cn.hutool.core.lang.Console;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.sax.handler.RowHandler;

import java.util.List;

public class ExcelSaxReader {
    public static void main(String[] args) throws Exception {
        ExcelUtil.readBySax("/Users/jmal/Downloads/欣薇尔工厂-数据报表 (4).xlsx", 0, createRowHandler());
    }

    private static RowHandler createRowHandler() {
        return new RowHandler() {
            @Override
            public void handle(int sheetIndex, long rowIndex, List<Object> rowlist) {
                Console.log("[{}] [{}] {}", sheetIndex, rowIndex, rowlist);
            }
        };
    }

}
