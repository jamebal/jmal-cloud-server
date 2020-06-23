package com.jmal.clouddisk.service.impl;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;
import static java.util.stream.Collectors.toList;

import java.io.*;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.*;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.service.IUserService;
import com.mongodb.client.AggregateIterable;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import sun.security.krb5.internal.Ticket;

/**
 * @Description 文件管理
 * @Author jmal
 * @Date 2020-01-14 13:05
 * @blame jmal
 */
@Service
@Slf4j
public class FileServiceImpl implements IFileService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    IUserService userService;

    @Autowired
    FilePropertie filePropertie;

    @Autowired
    IShareService shareService;

    private static final String COLLECTION_NAME = "fileDocument";
    private static final String CONTENT_TYPE_IMAGE = "image";
    private static final String CONTENT_TYPE_MARK_DOWN = "text/markdown";

    /***
     * 前端文件夹树的第一级的文件Id
     */
    private static final String FIRST_FILE_TREE_ID = "0";

    private static final AES aes = SecureUtil.aes();

    private Cache<String, CopyOnWriteArrayList<Integer>> resumeCache = CaffeineUtil.getResumeCache();
    private Cache<String, CopyOnWriteArrayList<Integer>> writtenCache = CaffeineUtil.getWrittenCache();
    private Cache<String, CopyOnWriteArrayList<Integer>> unWrittenCache = CaffeineUtil.getUnWrittenCacheCache();
    private Cache<String, Lock> chunkWriteLockCache = CaffeineUtil.getChunkWriteLockCache();

    /***
     * 文件列表
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> listFiles(UploadApiParam upload) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        Criteria criteria;
        String queryFileType = upload.getQueryFileType();
        if(!StringUtils.isEmpty(queryFileType)){
            switch (upload.getQueryFileType()){
                case "audio":
                    criteria = Criteria.where("contentType").regex("^audio");
                    break;
                case "video":
                    criteria = Criteria.where("contentType").regex("^video");
                    break;
                case "image":
                    criteria = Criteria.where("contentType").regex("^image");
                    break;
                case "text":
                    criteria = Criteria.where("suffix").in(Arrays.asList(filePropertie.getSimText()));
                    break;
                case "document":
                    criteria = Criteria.where("suffix").in(Arrays.asList(filePropertie.getDoument()));
                    break;
                default:
                    criteria = Criteria.where("path").is(currentDirectory);
                    break;
            }
        }else{
            criteria = Criteria.where("path").is(currentDirectory);
            Boolean isFolder = upload.getIsFolder();
            if(isFolder != null){
                criteria = Criteria.where("isFolder").is(isFolder);
            }
            Boolean isFavorite = upload.getIsFavorite();
            if(isFavorite != null){
                criteria = Criteria.where("isFavorite").is(isFavorite);
            }
        }
        List<FileDocument> list = getFileDocuments(upload, criteria);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteria));
        return result;
    }

    /***
     * 用户已使用空间
     * @param userId
     * @return
     * @throws CommonException
     */
    @Override
    public long takeUpSpace(String userId) throws CommonException {

        List<Bson> list = Arrays.asList(
                match(eq("userId", userId)),
                group(new BsonNull(), sum("totalSize", "$size")));
        AggregateIterable aggregateIterable =  mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        Document doc = (Document) aggregateIterable.first();
        if(doc != null){
            return doc.getLong("totalSize");
        }
        return 0;
    }

    private long getFileDocumentsCount(UploadApiParam upload, Criteria... criteriaList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(upload.getUserId()));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    private List<FileDocument> getFileDocuments(UploadApiParam upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        Integer pageSize = upload.getPageSize(), pageIndex = upload.getPageIndex();
        if (pageSize != null && pageIndex != null) {
            long skip = (pageIndex - 1) * pageSize;
            query.skip(skip);
            query.limit(pageSize);
        }
        String order = upload.getOrder();
        if(!StringUtils.isEmpty(order)){
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if("descending".equals(order)){
                direction = Sort.Direction.DESC;
            }
            query.with(new Sort(direction, sortableProp));
        }else{
            query.with(new Sort(Sort.Direction.DESC, "isFolder"));
        }
        query.fields().exclude("content").exclude("music.coverBase64");
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        long now = System.currentTimeMillis();

        list = list.parallelStream().peek(fileDocument -> {
            LocalDateTime updateDate = fileDocument.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileDocument.setAgoTime(now - update);
            if (fileDocument.getIsFolder()) {
                String path = fileDocument.getPath() + fileDocument.getName() + File.separator;
                long size = getFolderSize(fileDocument.getUserId(), path);
                fileDocument.setSize(size);
            }
        }).collect(toList());
        // 按文件名排序
        if(StringUtils.isEmpty(order)){
            list.sort(this::compareByFileName);
        }
        if(!StringUtils.isEmpty(order) && "name".equals(upload.getSortableProp())){
            list.sort(this::compareByFileName);
            list.sort(this::desc);
        }
        return list;
    }

    private int desc(FileDocument f1,FileDocument f2){
        return -1;
    }

    private List<FileDocument> getDirDocuments(UploadApiParam upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        query.addCriteria(Criteria.where("isFolder").is(true));
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 按文件名排序
        list.sort(this::compareByFileName);
        return list;
    }

    private Query getQuery(UploadApiParam upload, Criteria[] criteriaList) {
        String userId = upload.getUserId();
        if (StringUtils.isEmpty(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg() + "userId");
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return query;
    }

    @Override
    public ResponseResult<Object> searchFile(UploadApiParam upload, String keyword) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Criteria criteria1 = Criteria.where("name").regex(keyword);
        return getCountResponseResult(upload, result, criteria1);
    }

    private ResponseResult<Object> getCountResponseResult(UploadApiParam upload, ResponseResult<Object> result, Criteria... criteriaList) {
        List<FileDocument> list = getFileDocuments(upload, criteriaList);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteriaList));
        return result;
    }

    @Override
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParam upload, String id) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        assert fileDocument != null;
        String currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        Criteria criteria = Criteria.where("path").is(currentDirectory);
        return getCountResponseResult(upload, result, criteria);
    }

    private FileDocument getFileDocumentById(String fileId) {
        if (StringUtils.isEmpty(fileId) || FIRST_FILE_TREE_ID.equals(fileId)) {
            return null;
        }
        return mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
    }

    private String getRelativePathByFileId(FileDocument fileDocument) {
        if (fileDocument == null) {
            return getUserDirectory(null);
        }
        if (fileDocument.getIsFolder()) {
            return getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        String currentDirectory = fileDocument.getPath() + fileDocument.getName();
        return currentDirectory.replaceAll(filePropertie.getSeparator(), File.separator);
    }

    private String getUserDir(String userName) {
        return filePropertie.getRootDir() + File.separator + userName;
    }


    /***
     * 查找下级目录
     * @param upload
     * @param fileId
     * @return
     */
    @Override
    public ResponseResult<Object> queryFileTree(UploadApiParam upload, String fileId) {
        String currentDirectory = getUserDirectory(null);
        if (!StringUtils.isEmpty(fileId)) {
            FileDocument fileDocument = mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
            assert fileDocument != null;
            currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        Criteria criteria = Criteria.where("path").is(currentDirectory);
        List<FileDocument> list = getDirDocuments(upload, criteria);
        return ResultUtil.success(list);
    }

    /***
     * 根据文件名排序
     * @param f1
     * @param f2
     * @return
     */
    private int compareByFileName(FileDocument f1, FileDocument f2) {
        if (f1.getIsFolder() && !f2.getIsFolder()) {
            return -1;
        } else if (f1.getIsFolder() && f2.getIsFolder()) {
            return compareByName(f1, f2);
        } else if (!f1.getIsFolder() && f2.getIsFolder()) {
            return 1;
        } else {
            return compareByName(f1, f2);
        }
    }

    private int compareByName(FileDocument f1, FileDocument f2) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(f1.getName(), f2.getName());
    }

    /***
     * 统计文件夹的大小
     * @return
     */
    private long getFolderSize(String userId, String path) {
        List<Bson> list = Arrays.asList(
                match(and(eq("userId", userId),
                        eq("isFolder", false), regex("path", "^" + path))),
                group(new BsonNull(), sum("totalSize", "$size")));
        AggregateIterable<Document> aggregate = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        long totalSize = 0;
        Document doc = aggregate.first();
        if (doc != null) {
            Object object = doc.get("totalSize");
            if (object != null) {
                totalSize = Long.parseLong(object.toString());
            }
        }
        return totalSize;
    }

    /**
     * 查询附件
     *
     * @param id       文件id
     * @param username
     * @return
     * @throws IOException
     */
    @Override
    public Optional<Object> getById(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            setContent(username, fileDocument);
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public ResponseResult<Object> previewTextByPath(String path, String username) throws CommonException{
        File file = new File(Paths.get(filePropertie.getRootDir(),username,path).toString());
        if(!file.exists()){
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileDocument fileDocument = new FileDocument();
        if(file.length() > 1024 * 1024 * 5){
            fileDocument.setContentText(MyFileUtils.readLines(file,1000));
        }else{
            fileDocument.setContentText(FileUtil.readString(file,StandardCharsets.UTF_8));
        }
        return ResultUtil.success(fileDocument);
    }

    private void setContent(String username, FileDocument fileDocument) {
        if(StringUtils.isEmpty(fileDocument.getContentText())){
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            Path filepath = Paths.get(filePropertie.getRootDir(),username,currentDirectory,fileDocument.getName());
            if(Files.exists(filepath)){
                File file = filepath.toFile();
                if(file.length() > 1024 * 1024 * 5){
                    fileDocument.setContentText(MyFileUtils.readLines(file,1000));
                }else{
                    fileDocument.setContentText(FileUtil.readString(file,StandardCharsets.UTF_8));
                }
            }
        }
    }

    /***
     * 查看缩略图
     * @param id
     * @param username
     * @return
     */
    @Override
    public Optional<FileDocument> thumbnail(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            if(fileDocument.getContent() == null){
                String currentDirectory = getUserDirectory(fileDocument.getPath());
                File file = new File(filePropertie.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName());
                if(file.exists()){
                    fileDocument.setContent(FileUtil.readBytes(file));
                }
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public Optional<FileDocument> coverOfMp3(String id, String userName) throws CommonException {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if(fileDocument == null){
            return Optional.empty();
        }
        fileDocument.setContentType("image/png");
        fileDocument.setName("cover");
        String base64 = Optional.of(fileDocument).map(FileDocument::getMusic).map(Music::getCoverBase64).orElse("");
        fileDocument.setContent(Base64.decode(base64));
        return Optional.of(fileDocument);
    }

    /***
     * 获取文件信息
     * @param fileIds
     * @param username
     * @return
     */
    private FileDocument getFileInfo(List<String> fileIds, String username) throws CommonException{
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        int size = fileDocuments.size();
        if (size > 0) {
            FileDocument fileDocument = fileDocuments.get(0);
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String startPath = File.separator + username + currentDirectory;
            String filename = fileDocument.getName();
            if (size > 1) {
                filename = "download";
            }
            String filePath = startPath + filename;
            if (size == 1 && !fileDocument.getIsFolder()) {
                // 单个文件
                fileDocument.setPath(filePath);
            } else {
                fileDocument.setIsFolder(true);
                // 压缩文件夹
                String zipFilePath = filePath + ".zip";
                String zipFilename = filename + ".zip";

                List<Bson> selectFolders = new ArrayList<>();
                for (FileDocument document : fileDocuments) {
                    selectFolders.add(regex("path", "^" + document.getPath() + document.getName()));
                }

                List<String> selectFiles = new ArrayList<>();
                for (FileDocument document : fileDocuments) {
                    selectFiles.add(document.getName());
                }

                String parentPath = fileDocument.getPath();

                List<Bson> list = Arrays.asList(
                        match(and(eq("userId", fileDocument.getUserId()),
                                regex("path", "^" + parentPath))),
                        match(or(Arrays.asList(
                                or(selectFolders),
                                in("name", selectFiles)))));
                AggregateIterable<Document> aggregate = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);

                String temp = "/download/";
                StringBuilder res = new StringBuilder();
                for (Document doc : aggregate) {
                    if (doc != null) {
                        boolean isFolder = doc.getBoolean("isFolder");
                        if (!isFolder) {
                            String relativePath = doc.getString("path");
                            String relativeFileName = doc.getString("name");
                            long fileSize = doc.getLong("size");
                            try {
                                res.append(String.format("%s %d %s %s\n", "-", fileSize, URLEncoder.encode(File.separator + username + relativePath + relativeFileName, "UTF-8"), temp + relativePath.substring(parentPath.length()) + relativeFileName));
                            } catch (UnsupportedEncodingException e) {
                                throw new CommonException(-1,e.getMessage());
                            }
                        }
                    }
                }
                fileDocument.setPath(zipFilePath);
                fileDocument.setName(zipFilename);
                fileDocument.setContent(res.toString().getBytes(StandardCharsets.UTF_8));
            }
            return fileDocument;

        }
        return null;
    }

    /***
     * 交给nginx处理(共有的,任何人都和访问)
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws IOException
     */
    @Override
    public void publicNginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws CommonException {
        FileDocument f = mongoTemplate.findById(fileIds.get(0),FileDocument.class, COLLECTION_NAME);
        if(f != null){
            Consumer user = userService.userInfoById(f.getUserId());
            FileDocument fileDocument = getFileInfo(fileIds, user.getUsername());
            try {
                nginx(request, response, isDownload, fileDocument);
            } catch (IOException e) {
                throw new CommonException(-1,e.getMessage());
            }
        }
    }

    /***
     * 交给nginx处理(共有的,任何人都和访问)
     * @param request
     * @param response
     * @param relativePath
     * @param userId
     * @throws IOException
     */
    @Override
    public void publicNginx(HttpServletRequest request, HttpServletResponse response, String relativePath, String userId) throws CommonException {

        Consumer user = userService.userInfoById(userId);
        if(user == null){
            return;
        }
        String username = user.getUsername();
        String userDirectory = getUserFilePath(aes.decryptStr(relativePath));
        String absolutePath = File.separator + username + userDirectory;
        File file = new File(filePropertie.getRootDir() + absolutePath);
        String filename = FileUtil.getName(file);
        try{
            //获取浏览器名（IE/Chrome/firefox）目前主流的四大浏览器内核Trident(IE)、Gecko(Firefox内核)、WebKit(Safari内核,Chrome内核原型,开源)以及Presto(Opera前内核) (已废弃)
            String gecko = "Gecko", webKit = "WebKit";
            String userAgent = request.getHeader("User-Agent");
            if (userAgent.contains(gecko) || userAgent.contains(webKit)) {
                absolutePath = new String(absolutePath.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
                filename = new String(filename.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
            } else {
                absolutePath = URLEncoder.encode(absolutePath, "UTF-8");
                filename = URLEncoder.encode(filename, "UTF-8");
            }
            response.setHeader("Content-Type", FileContentTypeUtils.getContentType(FileUtil.extName(filename)));
            response.setHeader("X-Accel-Charset", "utf-8");
            response.setHeader("X-Accel-Redirect", absolutePath);
        }catch (Exception e){
            throw new CommonException(-1,e.getMessage());
        }
    }

    /***
     * 交给nginx处理
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws IOException
     */
    @Override
    public void nginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws CommonException {
        String username = userService.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN));
        FileDocument fileDocument = getFileInfo(fileIds, username);
        try {
            nginx(request, response, isDownload, fileDocument);
        } catch (IOException e) {
            throw new CommonException(-1,e.getMessage());
        }
    }

    private void nginx(HttpServletRequest request, HttpServletResponse response, boolean isDownload, FileDocument fileDocument) throws IOException {
        if (fileDocument != null) {
            String filename = fileDocument.getName();
            String path = fileDocument.getPath();
            //获取浏览器名（IE/Chrome/firefox）目前主流的四大浏览器内核Trident(IE)、Gecko(Firefox内核)、WebKit(Safari内核,Chrome内核原型,开源)以及Presto(Opera前内核) (已废弃)
            String gecko = "Gecko", webKit = "WebKit";
            String userAgent = request.getHeader("User-Agent");
            if (userAgent.contains(gecko) || userAgent.contains(webKit)) {
                path = new String(path.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
                filename = new String(filename.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
            } else {
                path = URLEncoder.encode(path, "UTF-8");
                filename = URLEncoder.encode(filename, "UTF-8");
            }
            response.setHeader("Content-Type", fileDocument.getContentType());
            response.setHeader("X-Accel-Charset", "utf-8");
            if (fileDocument.getIsFolder()) {
                response.setHeader("Content-Disposition", "attachment; filename=rwlock.zip");
                response.setHeader("X-Archive-Files", "zip");
                response.setHeader("X-Archive-Charset", "utf-8");
            } else {
                response.setHeader("X-Accel-Redirect", path);
            }
            if (isDownload) {
                response.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");
                OutputStream out = response.getOutputStream();
                if (fileDocument.getContent() != null) {
                    out.write(fileDocument.getContent());
                }
                out.flush();
            }
        }
    }

    /***
     * 重命名
     * @param username
     * @param id
     * @return
     */
    @Override
    public ResponseResult<Object> rename(String newFileName, String username, String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = filePropertie.getRootDir() + File.separator + username + currentDirectory;
            File file = new File(filePath + fileDocument.getName());
            if (fileDocument.getIsFolder()) {
                Query query = new Query();
                String searchPath = currentDirectory + fileDocument.getName();
                String newPath = currentDirectory + newFileName;
                query.addCriteria(Criteria.where("path").regex("^" + searchPath));
                List<FileDocument> documentList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
                // 修改该文件夹下的所有文件的path
                documentList.parallelStream().forEach(rep -> {
                    String path = rep.getPath();
                    String newFilePath = replaceStart(path, searchPath, newPath);
                    Update update = new Update();
                    update.set("path", newFilePath);
                    Query query1 = new Query();
                    query1.addCriteria(Criteria.where("_id").is(rep.getId()));
                    mongoTemplate.upsert(query1, update, COLLECTION_NAME);
                });
            }
            if (renameFile(newFileName, id, filePath, file)) {
                return ResultUtil.error("重命名失败");
            }
            return ResultUtil.success(true);
        } else {
            return ResultUtil.error("数据库查询失败");
        }
    }

    /***
     * 移动文件/文件夹
     * @param upload
     * @param froms 文件/文件夹id
     * @param to 文件夹id
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult move(UploadApiParam upload, List<String> froms, String to) {
        // 复制
        ResponseResult result = getCopyResult(upload, froms, to);
        if (result != null) {
            return result;
        }
        // 删除
        return delete(upload.getUsername(), froms);
    }

    private ResponseResult getCopyResult(UploadApiParam upload, List<String> froms, String to) {
        for (String from : froms) {
            ResponseResult result = copy(upload, from, to);
            if (result.getCode() != 0 && result.getCode() != -2) {
                return result;
            }
        }
        return null;
    }

    /***
     * 复制文件/文件夹
     * @param upload
     * @param froms 文件/文件夹id
     * @param to 文件夹id
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult copy(UploadApiParam upload, List<String> froms, String to) {
        // 复制
        ResponseResult result = getCopyResult(upload, froms, to);
        if (result != null) {
            return result;
        }
        return ResultUtil.success();
    }

    /***
     * 新建文档
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> newMarkdown(UploadApiParam upload) {
        upload.setIsFolder(false);
        String filename = upload.getFilename();
        String md5 = CalcMD5.getMd5(upload.getContentText());
        //用户磁盘目录
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        File file = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + currentDirectory + filename);
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        // 保存文件信息
        upload.setSuffix(FileUtil.extName(filename));
        FileDocument fileDocument = new FileDocument();
        fileDocument.setPath(currentDirectory);
        fileDocument.setSize(upload.getContentText().length());
        fileDocument.setContentType(CONTENT_TYPE_MARK_DOWN);
        fileDocument.setContentText(upload.getContentText());
        fileDocument.setMd5(md5);
        fileDocument.setName(filename);
        fileDocument.setIsFolder(false);
        saveFileInfo(upload, date, fileDocument);
        mongoTemplate.save(fileDocument, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> getMarkDownContent(String mark) throws CommonException {
        if (StringUtils.isEmpty(mark)) {
            Query query = new Query();
            query.addCriteria(Criteria.where("isFavorite").is(true));
            query.addCriteria(Criteria.where("contentType").is(CONTENT_TYPE_MARK_DOWN));
            query.with(new Sort(Sort.Direction.DESC,"uploadDate"));
            List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
            fileDocumentList = fileDocumentList.parallelStream().peek(fileDocument -> {
                Consumer user = userService.userInfoById(fileDocument.getUserId());
                String avatar = user.getAvatar();
                fileDocument.setUsername(user.getUsername());
                String filename = fileDocument.getName();
                fileDocument.setName(filename.substring(0,filename.length()-fileDocument.getSuffix().length()-1));
                LocalDateTime date = fileDocument.getUploadDate();
                String sb = date.getYear() + "年" +
                        date.getMonthValue() + "月" +
                        date.getDayOfMonth() + "日";
                fileDocument.setUploadTime(sb);
                fileDocument.setAvatar(avatar);
            }).collect(toList());
            return ResultUtil.success(fileDocumentList);
        } else {
            FileDocument fileDocument = mongoTemplate.findById(mark, FileDocument.class, COLLECTION_NAME);
            if (fileDocument != null) {
                if(fileDocument.getContentType().equals(CONTENT_TYPE_MARK_DOWN)){
                    String content = fileDocument.getContentText();
                    if(!StringUtils.isEmpty(content)){
                        content = replaceAll(content, fileDocument.getPath(), fileDocument.getUserId());
                        fileDocument.setContentText(content);
                    }
                }else{
                    String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
                    String currentDirectory = getUserDirectory(fileDocument.getPath());
                    File file = new File(filePropertie.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName());
                    fileDocument.setContentText(FileUtil.readString(file,StandardCharsets.UTF_8));
                }
            }
            return ResultUtil.success(fileDocument);
        }
    }

    /***
     * 编辑文档
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> editMarkdown(UploadApiParam upload) {
        FileDocument fileDocument = mongoTemplate.findById(upload.getFileId(), FileDocument.class, COLLECTION_NAME);
        String filename = upload.getFilename();
        //用户磁盘目录
        String currentDirectory = getUserDirectory(Objects.requireNonNull(fileDocument).getPath());
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        File file = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + currentDirectory + filename);
        FileUtil.del(filePropertie.getRootDir() + File.separator + upload.getUsername() + currentDirectory + fileDocument.getName());
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        Update update = new Update();
        update.set("size", FileUtil.size(file));
        update.set("name", upload.getFilename());
        update.set("contentText", upload.getContentText());
        update.set("updateDate",date);
        Query query = new Query().addCriteria(Criteria.where("_id").is(upload.getFileId()));
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> editMarkdownByPath(UploadApiParam upload) {
        File file = new File(Paths.get(filePropertie.getRootDir(),upload.getUsername(),upload.getRelativePath()).toString());
        if(!file.exists()){
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        return ResultUtil.success();
    }

    /***
     * 上传文档里的图片
     * @param upload
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult<Object> uploadMarkdownImage(UploadApiParam upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        try {
            String markName = upload.getFilename();
            upload.setTotalSize(multipartFile.getSize());
            upload.setIsFolder(false);
            String fileName = System.currentTimeMillis()+multipartFile.getOriginalFilename();
            upload.setFilename(fileName);
            upload.setRelativePath(fileName);

            String[] docPaths = new String[]{"Image","Document Image",markName};
            String docPath = "/Image/Document Image/"+markName;
            upload.setCurrentDirectory(docPath);
            //用户磁盘目录
            String userDirectoryFilePath = getUserDirectoryFilePath(upload);
            LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

            String username = upload.getUsername();
            String userId = upload.getUserId();
            String directoryPath = filePropertie.getRootDir() + File.separator + upload.getUsername() + getUserDirectory(docPath);
            File dir = new File(directoryPath);
            if(!dir.exists()){
                StringBuilder parentPath = new StringBuilder();
                for (int i = 0; i < docPaths.length; i++) {
                    UploadApiParam uploadApiParam = new UploadApiParam();
                    uploadApiParam.setIsFolder(true);
                    uploadApiParam.setFilename(docPaths[i]);
                    uploadApiParam.setUsername(username);
                    uploadApiParam.setUserId(userId);
                    if(i > 0){
                        uploadApiParam.setCurrentDirectory(parentPath.toString());
                    }
                    uploadFolder(uploadApiParam);
                    parentPath.append("/").append(docPaths[i]);
                }
            }
            File newFile = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
            // 保存文件信息
            upload.setInputStream(multipartFile.getInputStream());
            upload.setContentType(multipartFile.getContentType());
            upload.setSuffix(FileUtil.extName(fileName));
            FileDocument fileDocument = saveFileInfo(upload, upload.getTotalSize() + fileName, date);
            // 没有分片,直接存
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            return ResultUtil.success(fileDocument.getId());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResultUtil.error("添加图片失败");
    }

    /***
     * 上传用户图片
     * @param upload
     * @return
     * @throws CommonException
     */
    @Override
    public String uploadConsumerImage(UploadApiParam upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        try {
            upload.setTotalSize(multipartFile.getSize());
            upload.setIsFolder(false);
            String fileName = upload.getFilename();
            upload.setFilename(fileName);
            upload.setRelativePath(fileName);

            String[] docPaths = new String[]{"Image"};
            String docPath = "/Image";
            upload.setCurrentDirectory(docPath);
            //用户磁盘目录
            String userDirectoryFilePath = getUserDirectoryFilePath(upload);
            LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

            String username = upload.getUsername();
            String userId = upload.getUserId();
            String directoryPath = filePropertie.getRootDir() + File.separator + upload.getUsername() + getUserDirectory(docPath);
            File dir = new File(directoryPath);
            if(!dir.exists()){
                for (String path : docPaths) {
                    UploadApiParam uploadApiParam = new UploadApiParam();
                    uploadApiParam.setIsFolder(true);
                    uploadApiParam.setFilename(path);
                    uploadApiParam.setUsername(username);
                    uploadApiParam.setUserId(userId);
                    uploadFolder(uploadApiParam);
                }
            }
            // 没有分片,直接存
            File newFile = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
            // 保存文件信息
            upload.setInputStream(multipartFile.getInputStream());
            upload.setContentType(multipartFile.getContentType());
            upload.setSuffix(FileUtil.extName(fileName));
            FileDocument fileDocument = saveFileInfo(upload, upload.getTotalSize() + fileName, date);
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            return fileDocument.getId();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public FileDocument getById(String fileId) {
        return getFileDocumentById(fileId);
    }

    @Override
    public void createFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(filePropertie.getRootDir().length()+username.length()+1,fileAbsolutePath.length()-fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if(StringUtils.isEmpty(userId)){
            return;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));

        String suffix = FileUtil.extName(fileName);
        String contentType = FileContentTypeUtils.getContentType(suffix);

        // 文件是否存在
        boolean fileExists = mongoTemplate.exists(query,COLLECTION_NAME);
        if(fileExists){
            if(contentType.contains("audio")){
                Update update = new Update();
                Music music = AudioFileUtils.readAudio(file);
                update.set("music", music);
                mongoTemplate.upsert(query,update,COLLECTION_NAME);
            }
            return;
        }
        LocalDateTime nowDateTime = LocalDateTime.now(TimeUntils.ZONE_ID);
        Update update = new Update();
        update.set("userId",userId);
        update.set("name",fileName);
        update.set("path",relativePath);
        update.set("isFolder",file.isDirectory());
        update.set("uploadDate",nowDateTime);
        update.set("updateDate",nowDateTime);
        if(file.isFile()){
            long size = file.length();
            update.set("size", size);
            update.set("md5",size + relativePath + fileName);
            if(contentType.contains("audio")){
               Music music = AudioFileUtils.readAudio(file);
               update.set("music", music);
            }
            if (contentType.startsWith(CONTENT_TYPE_IMAGE)) {
                // 生成缩略图
                Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(file);
                thumbnail.size(256, 256);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    thumbnail.toOutputStream(out);
                    update.set("content", out.toByteArray());
                } catch (UnsupportedFormatException e) {
                    log.warn(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (contentType.contains(CONTENT_TYPE_MARK_DOWN)) {
                // 写入markdown内容
                String markDownContent = FileUtil.readString(file,StandardCharsets.UTF_8);
                update.set("contentText", markDownContent);
            }
            update.set("contentType", contentType);
            update.set("suffix",suffix);
            update.set("isFavorite",false);
        }
        mongoTemplate.upsert(query,update,COLLECTION_NAME);
    }

    @Override
    public void deleteFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(filePropertie.getRootDir().length()+username.length()+1,fileAbsolutePath.length()-fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if(StringUtils.isEmpty(userId)){
            return;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        // 文件是否存在
        boolean fileExists = mongoTemplate.exists(query,COLLECTION_NAME);
        if(fileExists){
            mongoTemplate.remove(query,COLLECTION_NAME);
        }
    }

    /***
     * 解压zip文件
     * @param fileId
     * @param destFileId
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult<Object> unzip(String fileId, String destFileId) throws CommonException {
        try {
            FileDocument fileDocument = getById(fileId);
            if(fileDocument == null){
                throw new CommonException(ExceptionType.FILE_NOT_FIND);
            }
            String username = userService.getUserNameById(fileDocument.getUserId());
            if(StringUtils.isEmpty(username)){
                throw new CommonException(ExceptionType.USER_NOT_FIND);
            }
            String filePath = getFilePathByFileId(username,fileDocument);

            String destDir;
            boolean isWrite = false;
            if(StringUtils.isEmpty(destFileId)){
                // 没有目标目录, 则预览解压到临时目录
                destDir = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),username,fileDocument.getName()).toString();
            }else{
                if(fileId.equals(destFileId)){
                    // 解压到当前文件夹
                    destDir = filePath.substring(0, filePath.length()-FileUtil.extName(new File(filePath)).length()-1);
                }else{
                    // 其他目录
                    FileDocument dest = getById(destFileId);
                    if(dest != null){
                        destDir = getFilePathByFileId(username,dest);
                    }else{
                        destDir = Paths.get(filePropertie.getRootDir(),username).toString();
                    }
                }
                isWrite = true;
            }

            CompressUtils.decompress(filePath, destDir, isWrite);
            return ResultUtil.success(listfile(username, destDir, !isWrite));
        } catch (Exception e){
            return ResultUtil.error("解压失败!");
        }
    }

    /***
     * 获取目录下的文件
     * @param path
     * @param username
     * @param tempDir
     * @return
     */
    @Override
    public ResponseResult<Object> listfiles(String path,String username, boolean tempDir) {
        String dirPath;
        if(tempDir){
            dirPath = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),username,path).toString();
        }else{
            dirPath = Paths.get(filePropertie.getRootDir(),username,path).toString();
        }
        return ResultUtil.success(listfile(username, dirPath, tempDir));
    }

    /***
     * 获取上一文件列表
     * @param path
     * @param username
     * @return
     */
    @Override
    public ResponseResult upperLevelList(String path, String username) {
        String upperLevel = Paths.get(filePropertie.getRootDir(),username,path).getParent().toString();
        if(Paths.get(filePropertie.getRootDir()).toString().equals(upperLevel)){
            upperLevel = Paths.get(filePropertie.getRootDir(),username).toString();
        }
        return ResultUtil.success(listfile(username, upperLevel, false));
    }

    public static void main(String[] args) {

        Path path = Paths.get("文件类型测试/asdfasdfa/asdfasdfas/asdfasdfa");
        System.out.println(path.equals(Paths.get("/文件类型测试/asdfasdfa/asdfasdfas/asdfasdfa")));

    }

    /***
     * 获取目录下的文件
     * @param username
     * @param dir
     */
    private List<FileDocument> listfile(String username, String dirPath, boolean tempDir) {
        File dir = new File(dirPath);
        if(!dir.exists()){
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        File[] fileList = dir.listFiles();
        List<FileDocument> list = Arrays.asList(fileList).stream().map(file -> {
            FileDocument fileDocument = new FileDocument();
            String filename = file.getName();
            String suffix = FileUtil.extName(filename);
            boolean isFolder = file.isDirectory();
            fileDocument.setName(filename);
            fileDocument.setIsFolder(isFolder);
            fileDocument.setSuffix(suffix);
            fileDocument.setContentType(FileContentTypeUtils.getContentType(suffix));
            String path;
            Path dirPaths = Paths.get(file.getPath());
            if(tempDir){
                path = dirPaths.subpath(Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),username).getNameCount(),dirPaths.getNameCount()).toString();
            }else{
                path = dirPaths.subpath(Paths.get(filePropertie.getRootDir(),username).getNameCount(),dirPaths.getNameCount()).toString();
            }
            fileDocument.setPath(path);
            return fileDocument;
        }).collect(toList());
        list.sort(this::compareByFileName);
        return list;
    }

    /***
     * 根据username 和 fileDocument 获取FilePath
     * @param username
     * @param fileDocument
     * @return
     * @throws CommonException
     */
    private String getFilePathByFileId(String username, FileDocument fileDocument) throws CommonException {
        StringBuilder sb = new StringBuilder();
        sb.append(filePropertie.getRootDir()).append(File.separator).append(username).append(getUserDirectory(fileDocument.getPath())).append(fileDocument.getName());
        Path path = Paths.get(sb.toString());
        if(!Files.exists(path)){
            throw new CommonException(ExceptionType.DIR_NOT_FIND);
        }

        return sb.toString();
    }

    private ResponseResult<Object> copy(UploadApiParam upload, String from, String to) {
        FileDocument formFileDocument = getFileDocumentById(from);
        String fromPath = getRelativePathByFileId(formFileDocument);
        String fromFilePath = getUserDir(upload.getUsername()) + fromPath;
        FileDocument toFileDocument = getFileDocumentById(to);
        String toPath = getRelativePathByFileId(toFileDocument);
        String toFilePath = getUserDir(upload.getUsername()) + toPath;
        if (formFileDocument != null) {
            if (formFileDocument.getIsFolder()) {
                // 复制文件夹
                // 复制其本身
                FileDocument copyFileDocument = copyFileDocument(formFileDocument, toPath);
                if (isExistsOfToCopy(copyFileDocument, toPath)) {
                    return ResultUtil.warning("所选目录已存在该文件夹!");
                }
                mongoTemplate.save(copyFileDocument, COLLECTION_NAME);
            } else {
                // 复制文件
                // 复制其本身
                FileDocument copyFileDocument = copyFileDocument(formFileDocument, toPath);
                if (isExistsOfToCopy(copyFileDocument, toPath)) {
                    return ResultUtil.warning("所选目录已存在该文件!");
                }
                mongoTemplate.save(copyFileDocument, COLLECTION_NAME);
            }
            FileUtil.copy(fromFilePath, toFilePath, true);
            return ResultUtil.success();
        }
        return ResultUtil.error("服务器开小差了, 请稍后再试...");
    }

    /***
     * 复制更新数据
     * @param formFileDocument
     * @param toPath
     * @return
     */
    private FileDocument copyFileDocument(FileDocument formFileDocument, String toPath) {
        formFileDocument.setId(null);
        formFileDocument.setPath(toPath);
        formFileDocument.setUpdateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        return formFileDocument;
    }

    /***
     * 目标目录是否存该文件
     * @param formFileDocument
     * @param toPath
     * @return
     */
    private boolean isExistsOfToCopy(FileDocument formFileDocument, String toPath) {
        Query query = new Query();
        query.addCriteria(Criteria.where("path").is(toPath));
        query.addCriteria(Criteria.where("name").is(formFileDocument.getName()));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    private static String replaceStart(String str, CharSequence searchStr, CharSequence replacement) {
        return replacement + str.substring(searchStr.length());
    }

    private boolean renameFile(String newFileName, String id, String filePath, File file) {
        if (file.renameTo(new File(filePath + newFileName))) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(id));
            Update update = new Update();
            update.set("name", newFileName);
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
        } else {
            return true;
        }
        return false;
    }

    /***
     * 保存文件(分片)
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> upload(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        int currentChunkSize = upload.getCurrentChunkSize();
        long totalSize = upload.getTotalSize();
        String filename = upload.getFilename();
        String md5 = upload.getIdentifier();
        MultipartFile file = upload.getFile();
        //用户磁盘目录
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        if (currentChunkSize == totalSize) {
            // 没有分片,直接存
            File chunkFile = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
            // 保存文件信息
            upload.setInputStream(file.getInputStream());
            upload.setContentType(file.getContentType());
            upload.setSuffix(FileUtil.extName(filename));
            saveFileInfo(upload, md5, date);
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            uploadResponse.setUpload(true);
        } else {
            // 多个分片
            // 落地保存文件
            // 这时保存的每个块, 块先存好, 后续会调合并接口, 将所有块合成一个大文件
            // 保存在用户的tmp目录下
            StringBuilder sb = new StringBuilder();
            sb.append(filePropertie.getRootDir()).append(File.separator)
                    .append(filePropertie.getChunkFileDir()).append(File.separator)
                    .append(upload.getUsername()).append(File.separatorChar)
                    .append(md5).append(File.separatorChar).append(upload.getChunkNumber());
            File chunkFile = new File(sb.toString());
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            setResumeCache(upload);
            uploadResponse.setUpload(true);
            // 检测是否已经上传完了所有分片,上传完了则需要合并
            if (checkIsNeedMerge(upload)) {
                uploadResponse.setMerge(true);
            }
            // 追加分片
            appendChunkFile(upload);
        }
        return ResultUtil.success(uploadResponse);
    }

    /***
     * 追加分片
     * @param upload
     */
    private void appendChunkFile(UploadApiParam upload) {
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
        Path filePath = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),upload.getUsername(),upload.getFilename());
        Lock lock = chunkWriteLockCache.get(md5,key -> new ReentrantLock());
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
                    Files.delete(filePath);
                }
                appenFile(upload, unWrittenChunks, writtenChunks);
            }
        }catch (Exception e){
            throw new CommonException(ExceptionType.FAIL_MERGA_FILE);
        }finally {
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
        File file = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),upload.getUsername(),md5,chunk+"").toFile();
        // 目标文件
        File outputFile = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),upload.getUsername(),upload.getFilename()).toFile();
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
        }catch (IOException e){
            throw new CommonException(ExceptionType.FAIL_MERGA_FILE);
        }
    }

    /***
     * 上传文件夹
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> uploadFolder(UploadApiParam upload) throws CommonException {
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        //没有分片,直接存
        File dir = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
//            if (!dir.mkdir()) {
//                String error = String.format("创建文件夹失败,dir:%s", dir.getAbsolutePath());
//                log.error(error);
//                return ResultUtil.error(error);
//            }
        }
        // 保存文件夹信息
        saveFolderInfo(upload, date);
        return ResultUtil.success();
    }

    /***
     * 新建文件夹
     * @param upload
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult<Object> newFolder(UploadApiParam upload) throws CommonException {
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        File dir = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);

        FileDocument fileDocument = new FileDocument();
        fileDocument.setIsFolder(true);
        fileDocument.setName(upload.getFilename());

        String path = getUserDirectory(upload.getCurrentDirectory());
        fileDocument.setPath(path);

        fileDocument.setUserId(upload.getUserId());
        fileDocument.setUploadDate(date);
        fileDocument.setUpdateDate(date);
        FileDocument res = mongoTemplate.save(fileDocument,COLLECTION_NAME);
        if(!dir.exists()){
            FileUtil.mkdir(dir);
        }
        return ResultUtil.success(res);
    }

    /***
     * 保存文件信息
     * @param upload
     * @param md5
     * @param date
     */
    private FileDocument saveFileInfo(UploadApiParam upload, String md5, LocalDateTime date) throws IOException {
        String contentType = upload.getContentType();
        FileDocument fileDocument = new FileDocument();
        String filename = upload.getFilename();
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        String relativePath = upload.getRelativePath();
        relativePath = relativePath.substring(0, relativePath.length() - filename.length());
        fileDocument.setPath(currentDirectory + relativePath);
        fileDocument.setSize(upload.getTotalSize());
        fileDocument.setContentType(contentType);
        fileDocument.setMd5(md5);
        fileDocument.setName(filename);
        fileDocument.setIsFolder(upload.getIsFolder());
        saveFileInfo(upload, date, fileDocument);
        if (contentType.startsWith(CONTENT_TYPE_IMAGE)) {
            // 生成缩略图
            Thumbnails.Builder<? extends InputStream> thumbnail = Thumbnails.of(upload.getInputStream());
            thumbnail.size(256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                thumbnail.toOutputStream(out);
                fileDocument.setContent(out.toByteArray());
            } catch (UnsupportedFormatException e) {
                log.warn(e.getMessage());
            }
        }
        if (contentType.contains(CONTENT_TYPE_MARK_DOWN)) {
            // 写入markdown内容
            byte[] content = toByteArray(upload.getInputStream());
            String markDownContent = new String(content,0,content.length,StandardCharsets.UTF_8);
            fileDocument.setContentText(markDownContent);
        }
        return saveFileInfo(fileDocument);
    }

    /***
     * 替换markdown中的图片url
     * @param input
     * @return
     */
    public static String replaceAll(CharSequence input, String path, String userId) throws CommonException {
        Pattern pattern = Pattern.compile("!\\[(.*)]\\((.*)\\)");
        Pattern pattern1 = Pattern.compile("(?<=]\\()[^)]+");
        Matcher matcher = pattern.matcher(input).usePattern(pattern1);
        matcher.reset();
        boolean result = matcher.find();
        if (result) {
            StringBuffer sb = new StringBuffer();
            do {
                //"/file/public/view?relativePath="+path + oldSrc +"&userId="+userId;
                String value = matcher.group(0);
                if(value.matches("(?!([hH][tT]{2}[pP]:/*|[hH][tT]{2}[pP][sS]:/*|[fF][tT][pP]:/*)).*?$+") && !value.startsWith("/file/public/image")){
                    String relativepath = aes.encryptBase64(path + value);
                    try {
                        relativepath = URLEncoder.encode(relativepath,"UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new CommonException(-1,e.getMessage());
                    }
                    String replacement = "/file/public/view?relativePath="+ relativepath +"&userId="+userId;
                    matcher.appendReplacement(sb, replacement);
                }
                result = matcher.find();
            } while (result);
            matcher.appendTail(sb);
            return sb.toString();
        }
        return input.toString();
    }

    /***
     * 部分文件信息
     * @param upload
     * @param date
     * @param fileDocument
     */
    private void saveFileInfo(UploadApiParam upload, LocalDateTime date, FileDocument fileDocument) {
        fileDocument.setIsFavorite(false);
        fileDocument.setUploadDate(date);
        fileDocument.setUpdateDate(date);
        fileDocument.setSuffix(upload.getSuffix());
        fileDocument.setUserId(upload.getUserId());
    }

    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    /***
     * 保存文件夹信息
     * @param upload
     * @param date
     */
    private void saveFolderInfo(UploadApiParam upload, LocalDateTime date) {
        String userId = upload.getUserId();
        String folderPath = upload.getFolderPath();
        String path = getUserDirectory(upload.getCurrentDirectory());
        if (!StringUtils.isEmpty(folderPath)) {
            path += folderPath;
        }
        String folderName = upload.getFilename();
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("isFolder").is(true));
        query.addCriteria(Criteria.where("path").is(path));
        query.addCriteria(Criteria.where("name").is(folderName));
        Update update = new Update();
        update.set("userId", userId);
        update.set("isFolder", true);
        update.set("path", path);
        update.set("name", folderName);
        update.set("uploadDate", date);
        update.set("updateDate", date);
        update.set("isFavorite", false);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    private FileDocument saveFileInfo(FileDocument fileDocument){
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(fileDocument.getUserId()));
        query.addCriteria(Criteria.where("isFolder").is(fileDocument.getIsFolder()));
        query.addCriteria(Criteria.where("path").is(fileDocument.getPath()));
        query.addCriteria(Criteria.where("name").is(fileDocument.getName()));
        FileDocument res = mongoTemplate.findOne(query,FileDocument.class,COLLECTION_NAME);
        if(res != null){
            Update update = new Update();
            update.set("size",fileDocument.getSize());
            update.set("md5",fileDocument.getMd5());
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
            res.setSize(fileDocument.getSize());
            res.setMd5(fileDocument.getMd5());
        }else{
            return mongoTemplate.save(fileDocument,COLLECTION_NAME);
        }
        return res;
    }

    private void setResumeCache(UploadApiParam upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        CopyOnWriteArrayList<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
        assert chunks != null;
        if (!chunks.contains(chunkNumber)) {
            chunks.add(chunkNumber);
            resumeCache.put(md5, chunks);
        }
    }

    /***
     * 获取已经保存的分片索引
     */
    private CopyOnWriteArrayList<Integer> getSavedChunk(UploadApiParam upload) {
        String md5 = upload.getIdentifier();
        return resumeCache.get(md5, key -> createResumeCache(upload));
    }

    /***
     * 检测是否需要合并
     */
    private boolean checkIsNeedMerge(UploadApiParam upload) {
        int totalChunks = upload.getTotalChunks();
        CopyOnWriteArrayList<Integer> chunkList = getSavedChunk(upload);
        return totalChunks == chunkList.size();
    }

    /***
     * 读取分片文件是否存在
     * @return
     */
    private CopyOnWriteArrayList<Integer> createResumeCache(UploadApiParam upload) {
        CopyOnWriteArrayList<Integer> resumeList = new CopyOnWriteArrayList<>();
        String md5 = upload.getIdentifier();
        // 读取tmp分片目录所有文件
        File f = new File(filePropertie.getRootDir() + File.separator + filePropertie.getChunkFileDir() + File.separator + upload.getUsername() + File.separator + md5);
        if (f.exists()) {
            // 排除目录，只要文件
            File[] fileArray = f.listFiles(pathName -> !pathName.isDirectory());
            if (fileArray != null) {
                if (fileArray.length > 0) {
                    for (File file : fileArray) {
                        // 分片文件
                        int resume = Integer.parseInt(file.getName());
                        resumeList.add(resume);
                    }
                }
            }
        }
        return resumeList;
    }

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> checkChunkUploaded(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        String md5 = upload.getIdentifier();
        String path = getUserDirectory(upload.getCurrentDirectory());

        String relativePath = upload.getRelativePath();
        path += relativePath.substring(0, relativePath.length() - upload.getFilename().length());
        FileDocument fileDocument = getByMd5(path, upload.getUserId(), md5);
        if (fileDocument != null) {
            // 文件已存在
            uploadResponse.setPass(true);
        } else {
            int totalChunks = upload.getTotalChunks();
            List<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
            // 返回已存在的分片
            uploadResponse.setResume(chunks);
            assert chunks != null;
            if (totalChunks == chunks.size()) {
                // 文件不存在,并且已经上传了所有的分片,则合并保存文件
                merge(upload);
            }
        }
        uploadResponse.setUpload(true);
        return ResultUtil.success(uploadResponse);
    }

    /***
     * 合并文件
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> merge(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        String md5 = upload.getIdentifier();
        File file = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),upload.getUsername(),upload.getFilename()).toFile();
        File outputFile = Paths.get(filePropertie.getRootDir(),upload.getUsername(),getUserDirectoryFilePath(upload)).toFile();
        // 清除缓存
        resumeCache.invalidate(md5);
        writtenCache.invalidate(md5);
        unWrittenCache.invalidate(md5);
        chunkWriteLockCache.invalidate(md5);
        File chunkDir = Paths.get(filePropertie.getRootDir(),filePropertie.getChunkFileDir(),upload.getUsername(),md5).toFile();
        FileUtil.del(chunkDir);
        FileUtil.move(file,outputFile,true);

        //保存文件信息
        upload.setInputStream(FileUtil.getInputStream(outputFile));
        String extName = FileUtil.extName(upload.getFilename());
        upload.setSuffix(extName);
        upload.setContentType(FileContentTypeUtils.getContentType(extName));
        saveFileInfo(upload, md5, LocalDateTime.now(TimeUntils.ZONE_ID));

        uploadResponse.setUpload(true);
        return ResultUtil.success(uploadResponse);
    }

    private static int compare(File o1, File o2) {
        if (Integer.parseInt(o1.getName()) < Integer.parseInt(o2.getName())) {
            return -1;
        }
        return 1;
    }

    /***
     * 用户磁盘目录
     * @param upload
     * @return String
     */
    private String getUserDirectoryFilePath(UploadApiParam upload) {
        String currentDirectory = upload.getCurrentDirectory();
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = filePropertie.getSeparator();
        }
        if (upload.getIsFolder()) {
            if (upload.getFolderPath() != null) {
                currentDirectory += filePropertie.getSeparator() + upload.getFolderPath();
            } else {
                currentDirectory += filePropertie.getSeparator() + upload.getFilename();
            }
        } else {
            currentDirectory += filePropertie.getSeparator() + upload.getRelativePath();
        }
        currentDirectory = currentDirectory.replaceAll(filePropertie.getSeparator(), File.separator);
        return currentDirectory;
    }

    private String getUserDirectory(String currentDirectory) {
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = filePropertie.getSeparator();
        } else {
            if (!currentDirectory.endsWith(filePropertie.getSeparator())) {
                currentDirectory += filePropertie.getSeparator();
            }
        }
        currentDirectory = currentDirectory.replaceAll(filePropertie.getSeparator(), File.separator);
        return currentDirectory;
    }

    private String getUserFilePath(String relativePath) {
        if (!StringUtils.isEmpty(relativePath)) {
            relativePath = relativePath.replaceAll(filePropertie.getSeparator(), File.separator);
        }
        return relativePath;
    }

    /***
     * 收藏文件或文件夹
     * @param fileIds
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult<Object> favorite(List<String> fileIds) throws CommonException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        update.set("isFavorite", true);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 取消收藏
     * @param fileIds
     * @return
     */
    @Override
    public ResponseResult<Object> unFavorite(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        update.set("isFavorite", false);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除文件
     * @param fileIds
     * @return
     */
    @Override
    public ResponseResult<Object> delete(String username, List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        boolean isDel = false;
        for (FileDocument fileDocument : fileDocuments) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = filePropertie.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName();
            File file = new File(filePath);
            isDel = FileUtil.del(file);
            if (fileDocument.getIsFolder()) {
                // 删除文件夹及其下的所有文件
                Query query1 = new Query();
                query1.addCriteria(Criteria.where("path").regex("^" + fileDocument.getPath() + fileDocument.getName()));
                mongoTemplate.remove(query1, COLLECTION_NAME);
                isDel = true;
            }
        }
        if (isDel) {
            mongoTemplate.remove(query, COLLECTION_NAME);
        } else {
            throw new CommonException(-1, "删除失败");
        }
        return ResultUtil.success();
    }

    /***
     * 是否存在该文件
     * @param userId
     * @param md5
     * @return
     */
    private FileDocument getByMd5(String path, String userId, String md5) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("md5").is(md5));
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
    }
}
