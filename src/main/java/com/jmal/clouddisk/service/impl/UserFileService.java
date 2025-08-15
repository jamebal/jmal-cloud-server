package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.jmal.clouddisk.service.IUserService.USER_ID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserFileService {

    private final FileProperties fileProperties;

    private final MongoTemplate mongoTemplate;

    private final CommonUserFileService commonUserFileService;

    public void deleteAllByUser(List<ConsumerDO> userList) {
        if (userList == null || userList.isEmpty()) {
            return;
        }
        userList.forEach(user -> {
            String username = user.getUsername();
            String userId = user.getId();
            PathUtil.del(Paths.get(fileProperties.getRootDir(), username));
            Query query = new Query();
            query.addCriteria(Criteria.where(USER_ID).in(userId));
            mongoTemplate.remove(query, FileServiceImpl.class);
        });
    }

    public String uploadConsumerImage(UploadApiParamDTO upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        String username = upload.getUsername();
        String userId = upload.getUserId();
        String fileName = upload.getFilename();
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        MimeType mimeType;
        try {
            mimeType = allTypes.forName(multipartFile.getContentType());
            fileName += mimeType.getExtension();
        } catch (MimeTypeException e) {
            log.error(e.getMessage(), e);
        }
        Path userImagePaths = Paths.get(fileProperties.getUserImgDir());
        // userImagePaths 不存在则新建
        commonUserFileService.upsertFolder(userImagePaths, username, userId);
        File newFile;
        try {
            if (commonUserFileService.getDisabledWebp(userId) || ("ico".equals(FileUtil.getSuffix(fileName)))) {
                newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
                FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            } else {
                fileName = fileName + Constants.POINT_SUFFIX_WEBP;
                newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
                BufferedImage image = ImageIO.read(multipartFile.getInputStream());
                commonUserFileService.imageFileToWebp(newFile, image);
            }
        } catch (IOException e) {
            throw new CommonException(2, "上传失败");
        }
        return commonUserFileService.createFile(username, newFile, userId, true);
    }

    public void setPublic(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("isPublic", true);
        mongoTemplate.updateFirst(query, update, FileServiceImpl.class);
    }

}
