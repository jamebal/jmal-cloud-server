package com.jmal.clouddisk.lucene;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ReadExcel {
    public static void main(String[] args) {
        try {
            FileInputStream fis = new FileInputStream(new File("/Users/jmal/Downloads/noname.xlsx"));
            XSSFWorkbook workbook = new XSSFWorkbook(fis);
            XSSFSheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String cellValue = cell.toString();
                    System.out.println("Cell Value: " + cellValue);
                }
            }
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
