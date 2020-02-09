package com.jmal.clouddisk.controller;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * @Description 云文件下载
 * @Author jmal
 * @Date 2020-01-27 12:59
 * @blame jmal
 */
@RestController
@RequestMapping("download")
public class DownloadController {

    @RequestMapping("/demo")
    public String downLoad(HttpServletResponse response) throws UnsupportedEncodingException {
        String filename="CAD2016.zip";
        File file = new File("/Users/jmal/Downloads/CAD2016.zip");
        if(file.exists()){
            //判断文件父目录是否存在
            response.setContentType("application/force-download");
            response.setHeader("Content-Disposition", "attachment;fileName="+filename);
            byte[] buffer = new byte[1024];
            //文件输入流
            FileInputStream fis = null;
            BufferedInputStream bis = null;
            //输出流
            OutputStream os;
            try {
                os = response.getOutputStream();
                fis = new FileInputStream(file);
                bis = new BufferedInputStream(fis);
                int i = bis.read(buffer);
                while(i != -1){
                    os.write(buffer);
                    i = bis.read(buffer);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("----------file download" + filename);
            try {
                assert bis != null;
                bis.close();
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
