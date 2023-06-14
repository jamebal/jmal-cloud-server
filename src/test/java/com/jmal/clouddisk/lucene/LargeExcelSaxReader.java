package com.jmal.clouddisk.lucene;

import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;

public class LargeExcelSaxReader {

    public static void main(String[] args) throws Exception {
        try (OPCPackage opcPackage = OPCPackage.open("/Users/jmal/Downloads/欣薇尔工厂-数据报表 (4).xlsx")) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(opcPackage);
            XSSFReader xssfReader = new XSSFReader(opcPackage);

            XSSFSheetXMLHandler.SheetContentsHandler sheetHandler = new SheetHandler();
            XSSFSheetXMLHandler handler = new XSSFSheetXMLHandler(
                    xssfReader.getStylesTable(), null, strings, sheetHandler, new DataFormatter(), false);

            XMLReader parser = SAXHelper.newXMLReader();
            parser.setContentHandler(handler);

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream stream = sheets.next()) {
                    String sheetName = sheets.getSheetName();
                    System.out.println("Processing sheet:" + sheetName);
                    InputSource sheetSource = new InputSource(stream);
                    parser.parse(sheetSource);
                }
            }
        }
    }

    private static class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        @Override
        public void startRow(int rowNum) {
            System.out.println("rowNum: " + rowNum);
        }

        @Override
        public void endRow(int rowNum) {

        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            System.out.println("Cell Value: " + formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {

        }
    }
}
