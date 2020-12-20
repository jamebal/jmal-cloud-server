package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.cglib.CglibUtil;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.Either;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.AggregateIterable;
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

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.descending;
import static java.util.stream.Collectors.toList;

/**
 * @author jmal
 * @Description 文档管理
 * @Date 2020/12/11 4:35 下午
 */
@Service
public class MarkdownService implements IMarkdownService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    IUserService userService;

    @Autowired
    FileProperties fileProperties;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TagService tagService;

    @Autowired
    FileServiceImpl fileService;

    private static final AES aes = SecureUtil.aes();

    @Override
    public ResponseResult<FileDocument> getMarkDownOne(ArticleDTO articleDTO) {
        String mark = articleDTO.getMark();
        if(StringUtils.isEmpty(mark)){
            return ResultUtil.success(new FileDocument());
        }
        FileDocument fileDocument = mongoTemplate.findById(mark, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        if (fileDocument != null) {
            String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
            fileDocument.setUsername(username);
            String currentDirectory = fileService.getUserDirectory(fileDocument.getPath());
            File file = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileDocument.getName()).toFile();
            String content = FileUtil.readString(file, StandardCharsets.UTF_8);
            fileDocument.setContentText(content);
        }
        return ResultUtil.success(fileDocument);
    }

    @Override
    public Page<List<MarkdownVO>> getArticles(Integer page, Integer pageSize) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        return getArticles(articleDTO);
    }

    @Override
    public List<MarkdownVO> getAlonePages() {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setIsAlonePage(true);
        return getMarkdownList(articleDTO).getData();
    }

    private Page<List<MarkdownVO>> getArticles(ArticleDTO articleDTO) {
        articleDTO.setIsRelease(true);
        ResponseResult<List<MarkdownVO>> responseResult = getMarkdownList(articleDTO);
        Page<List<MarkdownVO>> pageResult = new Page<>(articleDTO.getPageIndex() - 1, articleDTO.getPageSize(), Convert.toInt(responseResult.getCount()));
        pageResult.setData(responseResult.getData());
        return pageResult;
    }

    @Override
    public Page<List<MarkdownVO>> getArticlesByCategoryId(Integer page, Integer pageSize, String categoryId) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        if (!StringUtils.isEmpty(categoryId)) {
            articleDTO.setCategoryIds(new String[]{categoryId});
        }
        return getArticles(articleDTO);
    }

    @Override
    public Page<List<MarkdownVO>> getArticlesByTagId(int page, int pageSize, String tagId) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        if (!StringUtils.isEmpty(tagId)) {
            articleDTO.setTagIds(new String[]{tagId});
        }
        return getArticles(articleDTO);
    }

    @Override
    public Page<List<MarkdownVO>> getArticlesByKeyword(int page, int pageSize, String keyword) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        articleDTO.setKeyword(keyword);
        return getArticles(articleDTO);
    }

    @Override
    public Page<List<MarkdownVO>> getArticlesByAuthor(int page, int pageSize, String userId) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        articleDTO.setUserId(userId);
        return getArticles(articleDTO);
    }

    @Override
    public Page<Object> getArchives(Integer page, Integer pageSize) {
        boolean pagination = false;
        int skip = 0, limit = 10;
        if(page != null && pageSize != null){
            skip = (page - 1) * pageSize;
            limit = pageSize;
            pagination = true;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("release").is(true));
        long count = mongoTemplate.count(query, FileServiceImpl.COLLECTION_NAME);
        List<Bson> list = new ArrayList<>(Arrays.asList(
                match(and(eq("release", true), exists("alonePage", false))),
                sort(descending("uploadDate")),
                project(fields(computed("date", "$uploadDate"),
                        computed("day", eq("$dateToString", and(eq("format", "%Y-%m"), eq("date", "$uploadDate")))),
                        include("name"),
                        include("slug")))));
        if(pagination){
            list.add(skip(skip));
            list.add(limit(limit));
        }
        Map<String, List<ArchivesVO>> resultMap = new LinkedHashMap<>();
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(FileServiceImpl.COLLECTION_NAME).aggregate(list);
        for (Document doc : aggregateIterable) {
            String day = doc.getString("day");
            List<ArchivesVO> aList;
            if (resultMap.containsKey(day)) {
                aList = resultMap.get(day);
            } else {
                aList = new ArrayList<>();
            }
            ArchivesVO archivesVO = new ArchivesVO();
            String name = doc.getString("name");
            archivesVO.setName(name.substring(0, name.length() - 3));
            Date time = doc.get("date", Date.class);
            archivesVO.setDate(time);
            archivesVO.setId(doc.getObjectId("_id").toHexString());
            archivesVO.setSlug(doc.getString("slug"));
            aList.add(archivesVO);
            resultMap.put(day, aList);
        }
        Page<Object> pageResult;
        if(pagination){
            pageResult = new Page<>(page - 1, pageSize, Convert.toInt(count));
        } else {
            pageResult = new Page<>(Convert.toInt(count));
        }
        pageResult.setData(resultMap.values());
        return pageResult;
    }

    @Override
    public ArticleVO getMarkDownContentBySlug(String slug) {
        FileDocument fileDocument;
        if (StringUtils.isEmpty(slug)) {
            return null;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("slug").is(slug));
        fileDocument = mongoTemplate.findOne(query, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        if (fileDocument == null) {
            fileDocument = mongoTemplate.findById(slug, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        }
        if (fileDocument == null) {
            return null;
        }
        String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
        fileDocument.setUsername(username);
        String filename = fileDocument.getName();
        fileDocument.setName(filename.substring(0, filename.length() - fileDocument.getSuffix().length() - 1));
        ArticleVO articleVO = new ArticleVO();
        CglibUtil.copy(fileDocument, articleVO);

        if (articleVO.getCategoryIds() != null) {
            List<Category> categories = categoryService.getCategoryListByIds(articleVO.getCategoryIds());
            articleVO.setCategories(categories);
        }
        if (articleVO.getTagIds() != null) {
            List<Tag> tags = tagService.getTagListByIds(articleVO.getTagIds());
            articleVO.setTags(tags);
        }
        return articleVO;
    }

    @Override
    public ResponseResult<Object> sortMarkdown(List<String> fileIdList) {
        List<FileDocument> list = new ArrayList<>();
        for (int i = 0; i < fileIdList.size(); i++) {
            FileDocument fileDocument = new FileDocument();
            fileDocument.setPageSort(i);
            fileDocument.setId(fileIdList.get(i));
            list.add(fileDocument);
        }
        list.parallelStream().forEach(doc -> {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(doc.getId()));
            Update update = new Update();
            update.set("pageSort", doc.getPageSort());
            mongoTemplate.updateFirst(query, update, FileServiceImpl.COLLECTION_NAME);
        });
        return ResultUtil.success();
    }

    /***
     * 文档列表
     * @return List<MarkdownVO>
     */
    @Override
    public ResponseResult<List<MarkdownVO>> getMarkdownList(ArticleDTO articleDTO) {
        int skip = 0, limit = 0;
        long count = 0;
        Integer pageIndex = articleDTO.getPageIndex();
        Integer pageSize = articleDTO.getPageSize();
        if (pageIndex != null && pageSize != null) {
            skip = (pageIndex - 1) * pageSize;
            limit = pageSize;
        }
        Query query = new Query();
        // 查询条件
        boolean isDraft = false;
        query.addCriteria(Criteria.where("suffix").is("md"));
        if (!StringUtils.isEmpty(articleDTO.getUserId())) {
            query.addCriteria(Criteria.where("userId").is(articleDTO.getUserId()));
        }
        if (articleDTO.getIsRelease() != null && articleDTO.getIsRelease()) {
            query.addCriteria(Criteria.where("release").is(true));
        }
        if (articleDTO.getIsAlonePage() != null && articleDTO.getIsAlonePage()) {
            query.addCriteria(Criteria.where("alonePage").exists(true));
            query.with(new Sort(Sort.Direction.ASC, "pageSort"));
        } else {
            query.addCriteria(Criteria.where("alonePage").exists(false));
        }
        if (articleDTO.getIsDraft() != null && articleDTO.getIsDraft()) {
            isDraft = true;
            query.addCriteria(Criteria.where("draft").exists(true));
        }
        if (articleDTO.getCategoryIds() != null && articleDTO.getCategoryIds().length > 0) {
            query.addCriteria(Criteria.where("categoryIds").in(articleDTO.getCategoryIds()));
        }
        if (articleDTO.getTagIds() != null && articleDTO.getTagIds().length > 0) {
            query.addCriteria(Criteria.where("tagIds").in(articleDTO.getTagIds()));
        }
        if (!StringUtils.isEmpty(articleDTO.getKeyword())) {
            query.addCriteria(Criteria.where("contentText").regex(articleDTO.getKeyword(), "i"));
        }
        if(limit > 0){
            count = mongoTemplate.count(query, FileServiceImpl.COLLECTION_NAME);
        }
        // 排序
        if (!StringUtils.isEmpty(articleDTO.getSortableProp()) && !StringUtils.isEmpty(articleDTO.getOrder())) {
            if ("descending".equals(articleDTO.getOrder())) {
                query.with(new Sort(Sort.Direction.DESC, articleDTO.getSortableProp()));
            } else {
                query.with(new Sort(Sort.Direction.ASC, articleDTO.getSortableProp()));
            }
        } else {
            query.with(new Sort(Sort.Direction.DESC, "uploadDate"));
        }
        query.skip(skip);
        if(limit > 0){
            query.limit(limit);
        }
        List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        List<MarkdownVO> markdownVOList;
        boolean finalIsDraft = isDraft;
        markdownVOList = fileDocumentList.parallelStream().map(Either.wrap(fileDocument -> getMarkdownVO(fileDocument, finalIsDraft))).collect(toList());
        ResponseResult<List<MarkdownVO>> result = ResultUtil.success(markdownVOList);
        result.setCount(count);
        return result;
    }

    private MarkdownVO getMarkdownVO(FileDocument fileDocument, boolean isDraft) {
        MarkdownVO markdownVO = new MarkdownVO();
        if(isDraft){
            CglibUtil.copy(getFileDocument(fileDocument.getDraft()), markdownVO);
            markdownVO.setId(fileDocument.getId());
        } else {
            CglibUtil.copy(getFileDocument(fileDocument), markdownVO);
        }
        if(fileDocument.getDraft() != null){
            markdownVO.setDraft(true);
        }
        if (markdownVO.getCategoryIds() != null) {
            List<Category> categories = categoryService.getCategoryListByIds(markdownVO.getCategoryIds());
            markdownVO.setCategories(categories);
        }
        if (markdownVO.getTagIds() != null) {
            List<Tag> tags = tagService.getTagListByIds(markdownVO.getTagIds());
            markdownVO.setTags(tags);
        }
        return markdownVO;
    }

    /***
     * 修改fileDocument
     * 去掉文件名后缀
     * 添加用户头像
     * @param fileDocument FileDocument
     */
    private FileDocument getFileDocument(FileDocument fileDocument) {
        Consumer user = userService.userInfoById(fileDocument.getUserId());
        String avatar = user.getAvatar();
        fileDocument.setUsername(user.getUsername());
        fileDocument.setContentText(null);
        String filename = fileDocument.getName();
        fileDocument.setName(filename.substring(0, filename.length() - fileDocument.getSuffix().length() - 1));
        fileDocument.setAvatar(avatar);
        return fileDocument;
    }

    @Override
    public ResponseResult<Object> editMarkdown(ArticleParamDTO upload) {
        // 参数判断
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(upload.getFileId()));
        query1.addCriteria(Criteria.where("name").is(upload.getFilename()));
        if(mongoTemplate.exists(query1, FileServiceImpl.COLLECTION_NAME)){
            return ResultUtil.warning("该标题已存在");
        }
        boolean isDraft = false;
        boolean isUpdate = false;
        LocalDateTime nowDate = LocalDateTime.now(TimeUntils.ZONE_ID);
        LocalDateTime uploadDate = nowDate;
        if(upload.getUploadDate() != null){
            uploadDate = upload.getUploadDate();
        }
        FileDocument fileDocument = new FileDocument();
        Query query = new Query();
        if (upload.getFileId() != null) {
            // 修改
            isUpdate = true;
            query.addCriteria(Criteria.where("_id").is(upload.getFileId()));
            fileDocument = mongoTemplate.findById(upload.getFileId(), FileDocument.class, FileServiceImpl.COLLECTION_NAME);
            if(fileDocument == null){
                return ResultUtil.warning("该文档不存在");
            }
        }
        String filename = upload.getFilename();
        // 同步文档文件
        String currentDirectory = syncDocFile(upload, uploadDate, fileDocument, filename);
        fileDocument.setSuffix(FileUtil.extName(filename));
        fileDocument.setUserId(upload.getUserId());
        fileDocument.setUpdateDate(nowDate);
        fileDocument.setPath(currentDirectory);
        fileDocument.setSize(upload.getContentText().length());
        fileDocument.setContentType(FileServiceImpl.CONTENT_TYPE_MARK_DOWN);
        fileDocument.setMd5(CalcMd5.getMd5(filename + upload.getContentText()));
        fileDocument.setUploadDate(uploadDate);
        fileDocument.setName(filename);
        fileDocument.setCover(upload.getCover());
        fileDocument.setSlug(getSlug(upload));
        fileDocument.setCategoryIds(upload.getCategoryIds());
        fileDocument.setTagIds(tagService.getTagIdsByNames(upload.getTagNames()));
        fileDocument.setIsFolder(false);
        if(upload.getIsAlonePage() != null && upload.getIsAlonePage()){
            fileDocument.setAlonePage(upload.getIsAlonePage());
        }
        if(upload.getPageSort() != null){
            fileDocument.setPageSort(upload.getPageSort());
        }
        if (!StringUtils.isEmpty(upload.getIsDraft()) && upload.getIsDraft()) {
            isDraft = true;
        } else {
            fileDocument.setRelease(true);
            fileDocument.setContentText(upload.getContentText());
            fileDocument.setHtml(upload.getHtml());
        }
        if(!isDraft){
            fileDocument.setDraft(null);
        }
        Update update = MongoUtil.getUpdate(fileDocument);
        if (isDraft) {
            // 保存草稿
            if (isUpdate && !StringUtils.isEmpty(upload.getIsRelease())) {
                update = new Update();
            }
            fileDocument.setContentText(upload.getContentText());
            update.set("draft", fileDocument);
        } else {
            if(upload.getFileId() != null) {
                update.unset("draft");
            }
        }
        if(!isUpdate){
            FileDocument saved = mongoTemplate.save(fileDocument, FileServiceImpl.COLLECTION_NAME);
            upload.setFileId(saved.getId());
            query.addCriteria(Criteria.where("_id").is(saved.getId()));
        }
        mongoTemplate.upsert(query, update, FileServiceImpl.COLLECTION_NAME);
        return ResultUtil.success(upload.getFileId());
    }

    /***
     * 同步文档文件
     * @param upload ArticleParamDTO
     * @param uploadDate 发布日期
     * @param fileDocument FileDocument
     * @param filename filename
     * @return currentDirectory
     */
    private String syncDocFile(ArticleParamDTO upload, LocalDateTime uploadDate, FileDocument fileDocument, String filename) {
        String currentDirectory;
        if (!StringUtils.isEmpty(upload.getCurrentDirectory())) {
            currentDirectory = fileService.getUserDirectory(upload.getCurrentDirectory());
        } else {
            Path docPaths = Paths.get(fileProperties.getDocumentDir(), TimeUntils.getFileTimeStrOfMonth(uploadDate));
            // docImagePaths 不存在则新建
            upsertFolder(docPaths, upload.getUsername(), upload.getUserId());
            currentDirectory = fileService.getUserDirectory(docPaths.toString());
        }
        File file = Paths.get(fileProperties.getRootDir(), upload.getUsername(), currentDirectory, filename).toFile();
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        // 当有文件名和发布日期改变当话则把历史文件删掉
        if((!StringUtils.isEmpty(fileDocument.getName()) && !filename.equals(fileDocument.getName())) || (!StringUtils.isEmpty(fileDocument.getPath()) && !currentDirectory.equals(fileDocument.getPath()))){
            Path oldPath = Paths.get(fileProperties.getRootDir(), upload.getUsername(), fileDocument.getPath(), fileDocument.getName());
            if(Files.exists(oldPath)){
                FileUtil.del(oldPath);
            }
        }
        return currentDirectory;
    }

    @Override
    public ResponseResult<Object> deleteDraft(String fileId, String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        if(fileDocument == null || fileDocument.getDraft() == null){
            return ResultUtil.success();
        }
        File draftFile = Paths.get(fileProperties.getRootDir(), username, fileDocument.getDraft().getPath(), fileDocument.getDraft().getName()).toFile();
        FileUtil.del(draftFile);

        File file = Paths.get(fileProperties.getRootDir(), username, fileDocument.getPath(), fileDocument.getName()).toFile();
        FileUtil.writeString(fileDocument.getContentText(), file, StandardCharsets.UTF_8);

        Update update = new Update();
        update.unset("draft");
        mongoTemplate.upsert(query, update, FileServiceImpl.COLLECTION_NAME);
        return ResultUtil.success();
    }

    private String getSlug(ArticleParamDTO upload) {
        Query query = new Query();
        String slug = upload.getSlug();
        if (StringUtils.isEmpty(slug)) {
            return upload.getFilename();
        }
        String fileId = upload.getFileId();
        if (fileId != null) {
            query.addCriteria(Criteria.where("_id").nin(fileId));
        }
        query.addCriteria(Criteria.where("slug").is(slug));
        if (mongoTemplate.exists(query, FileServiceImpl.COLLECTION_NAME)) {
            return slug + "-1";
        }
        return slug;
    }

    @Override
    public ResponseResult<Object> editMarkdownByPath(UploadApiParamDTO upload) {
        File file = new File(Paths.get(fileProperties.getRootDir(), upload.getUsername(), upload.getRelativePath()).toString());
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        fileService.updateFile(upload.getUsername(), file);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> uploadMarkdownImage(UploadImageDTO upload) throws CommonException {
        List<Map<String, String>> list = new ArrayList<>();
        MultipartFile[] multipartFiles = upload.getFiles();
        if (multipartFiles != null) {
            for (MultipartFile multipartFile : multipartFiles) {
                list.add(uploadImage(upload, multipartFile));
            }
        }
        return ResultUtil.success(list);
    }

    @Override
    public ResponseResult<Object> uploadMarkdownLinkImage(UploadImageDTO upload) {
        if(StringUtils.isEmpty(upload.getUrl())){
            return ResultUtil.warning("url不能为空");
        }
        Map<String, String> map = new HashMap<>(2);
        String username = upload.getUsername();
        String userId = upload.getUserId();
        Path docImagePaths = Paths.get(fileProperties.getDocumentImgDir(), TimeUntils.getFileTimeStrOfMonth());
        // docImagePaths 不存在则新建
        upsertFolder(docImagePaths, username, userId);
        File newFile;
        try {
            newFile = HttpUtil.downloadFileFromUrl(upload.getUrl(), Paths.get(fileProperties.getRootDir(), username, docImagePaths.toString()).toString());
        } catch (HttpException e) {
            return ResultUtil.error("上传失败");
        }
        if (!fileProperties.getMonitor() || fileProperties.getTimeInterval() >= 3L) {
            fileService.createFile(username, newFile);
        }
        String filepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode("/file/" + Paths.get(username, docImagePaths.toString(), newFile.getName()), StandardCharsets.UTF_8);
        map.put("url", filepath);
        map.put("originalURL", upload.getUrl());
        return ResultUtil.success(map);
    }

    /***
     * 上传成功后返回文件名和路径
     * @param upload UploadImageDTO
     * @param multipartFile MultipartFile
     * @return
     * {
     *    "filename1": "filepath1",
     *    "filename2": "filepath2"
     *    }
     */
    private Map<String, String> uploadImage(UploadImageDTO upload, MultipartFile multipartFile) {
        Map<String, String> map = new HashMap<>(2);
        String fileName = TimeUntils.getFileTimeStrOfDay() + multipartFile.getOriginalFilename();
        Path docImagePaths = Paths.get(fileProperties.getDocumentImgDir(), TimeUntils.getFileTimeStrOfMonth());
        String username = upload.getUsername();
        String userId = upload.getUserId();
        // docImagePaths 不存在则新建
        upsertFolder(docImagePaths, username, userId);
        File newFile = Paths.get(fileProperties.getRootDir(), username, docImagePaths.toString(), fileName).toFile();
        try {
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
        } catch (IOException e) {
            new CommonException(2, "上传失败");
        }
        if (!fileProperties.getMonitor() || fileProperties.getTimeInterval() >= 3L) {
            fileService.createFile(username, newFile);
        }
        map.put("filename", fileName);
        String filepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode("/file/" + Paths.get(username, docImagePaths.toString(), fileName), StandardCharsets.UTF_8);
        map.put("filepath", filepath);
        return map;
    }

    /***
     * 如果文件夹不存在，则创建
     * @param docPaths  文件夹path
     * @param username username
     * @param userId userId
     */
    private void upsertFolder(@NotNull Path docPaths, @NotNull String username, @NotNull String userId) {
        File dir = Paths.get(fileProperties.getRootDir(), username, docPaths.toString()).toFile();
        if (!dir.exists()) {
            StringBuilder parentPath = new StringBuilder();
            for (int i = 0; i < docPaths.getNameCount(); i++) {
                String name = docPaths.getName(i).toString();
                UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
                uploadApiParamDTO.setIsFolder(true);
                uploadApiParamDTO.setFilename(name);
                uploadApiParamDTO.setUsername(username);
                uploadApiParamDTO.setUserId(userId);
                if (i > 0) {
                    uploadApiParamDTO.setCurrentDirectory(parentPath.toString());
                }
                fileService.uploadFolder(uploadApiParamDTO);
                parentPath.append("/").append(name);
            }
        }
    }

    /***
     * 替换markdown中的图片url
     * @param input input
     * @return String
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
                //"/public/view?relativePath="+path + oldSrc +"&userId="+userId;
                String value = matcher.group(0);
                if (value.matches("(?!([hH][tT]{2}[pP]:/*|[hH][tT]{2}[pP][sS]:/*|[fF][tT][pP]:/*)).*?$+") && !value.startsWith("/file/public/image")) {
                    String relativepath = aes.encryptBase64(path + value);
                    try {
                        relativepath = URLEncoder.encode(relativepath, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new CommonException(-1, e.getMessage());
                    }
                    String replacement = "/api/public/view?relativePath=" + relativepath + "&userId=" + userId;
                    matcher.appendReplacement(sb, replacement);
                }
                result = matcher.find();
            } while (result);
            matcher.appendTail(sb);
            return sb.toString();
        }
        return input.toString();
    }
}
