package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.Either;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
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
    public Page<Object> getArticles(Integer page, Integer pageSize) {
        return getArticles(page, pageSize, null, null);
    }

    @Override
    public List<MarkdownVO> getAlonePages() {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setIsAlonePage(true);
        return getMarkdownList(articleDTO).getData();
    }

    private Page<Object> getArticles(Integer page, Integer pageSize, String categoryId, String tagId) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        articleDTO.setIsRelease(true);
        if (!StringUtils.isEmpty(categoryId)) {
            articleDTO.setCategoryIds(new String[]{categoryId});
        }
        if (!StringUtils.isEmpty(tagId)) {
            articleDTO.setTagIds(new String[]{tagId});
        }
        ResponseResult<List<MarkdownVO>> responseResult = getMarkdownList(articleDTO);
        Page<Object> pageResult = new Page<Object>(page - 1, pageSize, Convert.toInt(responseResult.getCount()));
        pageResult.setData(responseResult.getData());
        return pageResult;
    }

    @Override
    public Page<Object> getArticlesByCategoryId(Integer page, Integer pageSize, String categoryId) {
        return getArticles(page, pageSize, categoryId, null);
    }

    @Override
    public Page<Object> getArticlesByTagId(int page, int pageSize, String tagId) {
        return getArticles(page, pageSize, null, tagId);
    }

    @Override
    public Page<Object> getArchives(Integer page, Integer pageSize) {
        int skip = 0, limit = 5;
        if (page != null && pageSize != null) {
            skip = (page - 1) * pageSize;
            limit = pageSize;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("release").is(true));
        long count = mongoTemplate.count(query, FileServiceImpl.COLLECTION_NAME);

        List list = Arrays.asList(
                match(and(eq("release", true), exists("alonePage", false))),
                sort(descending("uploadDate")),
                project(fields(computed("date", "$uploadDate"),
                        computed("day", eq("$dateToString", and(eq("format", "%Y-%m"), eq("date", "$uploadDate")))),
                        include("name"),
                        include("slug"))),
                skip(skip),
                limit(limit));
        Map<String, List<ArchivesVO>> resutMap = new LinkedHashMap<>();
        AggregateIterable aggregateIterable = mongoTemplate.getCollection(FileServiceImpl.COLLECTION_NAME).aggregate(list);
        MongoCursor<Document> cursor = aggregateIterable.iterator();
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String day = doc.getString("day");
            List<ArchivesVO> aList;
            if (resutMap.containsKey(day)) {
                aList = resutMap.get(day);
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
            resutMap.put(day, aList);
        }
        Page<Object> pageResult = new Page<Object>(page - 1, pageSize, Convert.toInt(count));
        pageResult.setData(resutMap.values());
        return pageResult;
    }

    @Override
    public FileDocument getMarkDownContentBySlug(String slug) {
        FileDocument fileDocument = null;
        if (StringUtils.isEmpty(slug)) {
            return fileDocument;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("slug").is(slug));
        fileDocument = mongoTemplate.findOne(query, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        if (fileDocument == null) {
            fileDocument = mongoTemplate.findById(slug, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        }
        if (fileDocument == null) {
            return fileDocument;
        }
        String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
        fileDocument.setUsername(username);
        String filename = fileDocument.getName();
        fileDocument.setName(filename.substring(0, filename.length() - fileDocument.getSuffix().length() - 1));
        return fileDocument;
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
     * @param skip
     * @param limit
     * @return
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
            query.addCriteria(Criteria.where("name").regex(articleDTO.getKeyword(), "i"));
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
        List<MarkdownVO> markdownVOList = new ArrayList<>();
        boolean finalIsDraft = isDraft;
        markdownVOList = fileDocumentList.parallelStream().map(Either.wrap(fileDocument -> {
            return getMarkdownVO(fileDocument, finalIsDraft);
        })).collect(toList());
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
        return markdownVO;
    }

    /***
     * 修改fileDocument
     * 去掉文件名后缀
     * 添加用户头像
     * @param fileDocument
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
        FileDocument fileDocument = new FileDocument();
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        Query query = new Query();
        if (upload.getFileId() != null) {
            // 修改
            isUpdate = true;
            query.addCriteria(Criteria.where("_id").is(upload.getFileId()));
            fileDocument = mongoTemplate.findById(upload.getFileId(), FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        } else {
            // 新增
            fileDocument.setUploadDate(date);
        }
        String filename = upload.getFilename();
        String currentDirectory = fileService.getUserDirectory(upload.getCurrentDirectory());
        File file = Paths.get(fileProperties.getRootDir(), upload.getUsername(), currentDirectory, filename).toFile();
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        if(!StringUtils.isEmpty(fileDocument.getName()) && !filename.equals(fileDocument.getName())){
            // 如果是修改的话则把历史文件删掉
            Path oldPath = Paths.get(fileProperties.getRootDir(), upload.getUsername(), fileDocument.getPath(), fileDocument.getName());
            if(Files.exists(oldPath)){
                FileUtil.del(oldPath);
            }
        }
        fileDocument.setSuffix(FileUtil.extName(filename));
        fileDocument.setUserId(upload.getUserId());
        fileDocument.setUpdateDate(date);
        fileDocument.setPath(currentDirectory);
        fileDocument.setSize(upload.getContentText().length());
        fileDocument.setContentType(FileServiceImpl.CONTENT_TYPE_MARK_DOWN);
        fileDocument.setMd5(CalcMd5.getMd5(filename + upload.getContentText()));
        if(upload.getUploadDate() != null){
            fileDocument.setUploadDate(upload.getUploadDate());
        }
        if(upload.getIsAlonePage() != null && upload.getIsAlonePage()){
            fileDocument.setAlonePage(upload.getIsAlonePage());
        }
        if(upload.getPageSort() != null){
            fileDocument.setPageSort(upload.getPageSort());
        }
        fileDocument.setName(filename);
        fileDocument.setCover(upload.getCover());
        fileDocument.setSlug(getSlug(upload));
        fileDocument.setCategoryIds(upload.getCategoryIds());
        fileDocument.setTagIds(tagService.getTagIdsByNames(upload.getTagNames()));
        fileDocument.setIsFolder(false);
        if (!StringUtils.isEmpty(upload.getIsDraft()) && upload.getIsDraft()) {
            isDraft = true;
        } else {
            fileDocument.setRelease(true);
            fileDocument.setContentText(upload.getContentText());
        }
        if(!isDraft){
            fileDocument.setDraft(null);
        }
        Update update = MongoUtil.getUpdate(fileDocument);
        if (isDraft) {
            // 保存草稿
            FileDocument result;
            String fileId = upload.getFileId();
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

    @Override
    public ResponseResult<Object> deleteDraft(String fileId, String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, FileServiceImpl.COLLECTION_NAME);
        if(fileDocument.getDraft() == null){
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

    /***
     * 上传成功后返回文件名和路径
     * @param upload
     * @param multipartFile
     * @return
     */
    private Map<String, String> uploadImage(UploadImageDTO upload, MultipartFile multipartFile) {
        Map<String, String> map = new HashMap<>(2);
        String markName = upload.getFilename();
        String fileName = System.currentTimeMillis() + multipartFile.getOriginalFilename();
        Path docPaths = Paths.get(fileProperties.getDocumentImgDir(), markName);
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        String username = upload.getUsername();
        String userId = upload.getUserId();
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
        File newFile = Paths.get(fileProperties.getRootDir(), username, docPaths.toString(), fileName).toFile();
        try {
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
        } catch (IOException e) {
            new CommonException(2, "上传失败");
        }
        String fileId = null;
        if (!fileProperties.getMonitor() || fileProperties.getTimeInterval() >= 3L) {
            fileId = fileService.createFile(username, newFile);
        }
        map.put("filename", fileName);
        String filepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode("/file/" + Paths.get(username, docPaths.toString(), fileName), StandardCharsets.UTF_8);
        map.put("filepath", filepath);
        return map;
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
