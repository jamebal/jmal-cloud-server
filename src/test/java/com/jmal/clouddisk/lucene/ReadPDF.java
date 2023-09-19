package com.jmal.clouddisk.lucene;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class ReadPDF {
    public static void main(String[] args) {
        try {
            File file = new File("/Users/jmal/Downloads/PDF_副本.pdf");
            PDDocument document = PDDocument.load(file);

            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);

            System.out.println(text);

            document.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
