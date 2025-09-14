package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileBase;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.service.Constants;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;

public class FileSortService {

    public static List<FileIntroVO> sortByFileName(UploadApiParamDTO upload, List<FileIntroVO> fileIntroVOList, String order) {
        // 按文件名排序
        if (CharSequenceUtil.isBlank(order)) {
            fileIntroVOList = fileIntroVOList.stream().sorted(FileSortService::compareByFileName).toList();
        }
        if (!CharSequenceUtil.isBlank(order) && Constants.FILENAME_FIELD.equals(upload.getSortableProp())) {
            fileIntroVOList = fileIntroVOList.stream().sorted(FileSortService::compareByFileName).toList();
            if (Constants.DESCENDING.equals(order)) {
                fileIntroVOList = fileIntroVOList.stream().sorted(FileSortService::desc).toList();
            }
        }
        return fileIntroVOList;
    }

    public static int desc(FileBase f1, FileBase f2) {
        return -1;
    }

    /***
     * 根据文件名排序
     * @param f1 f1
     * @param f2 f2
     */
    public static int compareByFileName(FileBase f1, FileBase f2) {
        if (Boolean.TRUE.equals(f1.getIsFolder()) && !f2.getIsFolder()) {
            return -1;
        } else if (f1.getIsFolder() && f2.getIsFolder()) {
            return compareByName(f1, f2);
        } else if (!f1.getIsFolder() && Boolean.TRUE.equals(f2.getIsFolder())) {
            return 1;
        } else {
            return compareByName(f1, f2);
        }
    }

    public static int compareByName(FileBase f1, FileBase f2) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(f1.getName(), f2.getName());
    }

}
