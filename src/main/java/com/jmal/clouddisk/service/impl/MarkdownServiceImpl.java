package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.WebConfig;
import com.jmal.clouddisk.dao.IArticleDAO;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.Either;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.LuceneService;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author jmal
 * @Description 文档管理
 * @Date 2020/12/11 4:35 下午
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarkdownServiceImpl implements IMarkdownService {

    private final IUserService userService;

    private final FileProperties fileProperties;

    private final CategoryService categoryService;

    private final SettingService settingService;

    private final TagService tagService;

    private final CommonFileService commonFileService;

    private final CommonUserFileService commonUserFileService;

    private final ApplicationEventPublisher eventPublisher;

    private final LuceneService luceneService;

    private final LogService logService;

    private final UserLoginHolder userLoginHolder;

    private final IArticleDAO articleDAO;

    private final IFileDAO fileDAO;


    @Override
    public ResponseResult<FileDocument> getMarkDownOne(ArticleDTO articleDTO) {
        String mark = articleDTO.getMark();
        if (CharSequenceUtil.isBlank(mark)) {
            return ResultUtil.success(new FileDocument());
        }
        FileDocument fileDocument = articleDAO.getMarkdownOne(mark);
        if (fileDocument == null) {
            return ResultUtil.warning("该文章不存在");
        }
        String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
        fileDocument.setUsername(username);
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
    public Urlset getSitemapXml() {
        String siteUrl = getSiteUrl();
        List<FileDocument> fileDocumentList = getArticlesUrl();
        Urlset urlset = new Urlset();
        List<Urlset.Url> urlList = new ArrayList<>();
        fileDocumentList.forEach(fileDocument -> {
            Urlset.Url url = new Urlset.Url();
            String prefix = "/s/";
            if (fileDocument.getAlonePage() != null && fileDocument.getAlonePage()) {
                prefix = "/o/";
            }
            String slug = fileDocument.getSlug();
            if (CharSequenceUtil.isBlank(slug)) {
                slug = fileDocument.getId();
            }
            url.setLoc(siteUrl + prefix + slug);
            url.setLastmod(LocalDateTimeUtil.format(fileDocument.getUpdateDate(), "yyyy-MM-dd"));
            urlList.add(url);
        });
        urlset.setUrl(urlList);
        return urlset;
    }

    @Override
    public String getSitemapTxt() {
        String siteUrl = getSiteUrl();
        List<FileDocument> fileDocumentList = getArticlesUrl();
        StringBuilder stringBuilder = new StringBuilder();
        fileDocumentList.forEach(fileDocument -> {
            stringBuilder.append(siteUrl);
            if (fileDocument.getAlonePage() != null && fileDocument.getAlonePage()) {
                stringBuilder.append("/o/");
            } else {
                stringBuilder.append("/s/");
            }
            String slug = fileDocument.getSlug();
            if (CharSequenceUtil.isBlank(slug)) {
                stringBuilder.append(fileDocument.getId());
            } else {
                stringBuilder.append(slug);
            }
            stringBuilder.append("\r\n");
        });
        return stringBuilder.toString();
    }

    private String getSiteUrl() {
        String siteUrl = settingService.getWebsiteSetting().getSiteUrl();
        if (siteUrl.endsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
        return siteUrl;
    }

    private List<FileDocument> getArticlesUrl() {
        return articleDAO.getAllReleaseArticles();
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
        if (!CharSequenceUtil.isBlank(categoryId)) {
            articleDTO.setCategoryIds(new String[]{categoryId});
        }
        return getArticles(articleDTO);
    }

    @Override
    public Page<List<MarkdownVO>> getArticlesByTagId(int page, int pageSize, String tagId) {
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setPageIndex(page);
        articleDTO.setPageSize(pageSize);
        if (!CharSequenceUtil.isBlank(tagId)) {
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
        boolean pagination = page != null && pageSize != null;
        org.springframework.data.domain.Page<ArchivesVO> archivesVOPage = articleDAO.getArchives(page, pageSize);
        Map<String, List<ArchivesVO>> resultMap = new LinkedHashMap<>();
        for (ArchivesVO proj : archivesVOPage.getContent()) {
            String day = proj.getDay();
            resultMap.computeIfAbsent(day, _ -> new ArrayList<>());
            ArchivesVO archivesVO = new ArchivesVO();
            archivesVO.setId(proj.getId());
            String name = proj.getName();
            archivesVO.setName(name.substring(0, name.length() - 3));
            archivesVO.setSlug(proj.getSlug());
            archivesVO.setDate(proj.getDate());
            resultMap.get(day).add(archivesVO);
        }

        if (page == null) {
            page = 1;
        }
        if (pageSize == null) {
            pageSize = 10;
        }
        Page<Object> pageResult;
        if (pagination) {
            pageResult = new Page<>(page - 1, pageSize, Convert.toInt(archivesVOPage.getTotalElements()));
        } else {
            pageResult = new Page<>(Convert.toInt(archivesVOPage.getTotalElements()));
        }
        pageResult.setData(resultMap.values());
        return pageResult;
    }

    @Override
    public ArticleVO getMarkDownContentBySlug(String slug) {
        ArticleVO articleVO;
        if (CharSequenceUtil.isBlank(slug)) {
            return null;
        }
        articleVO = articleDAO.findBySlug(slug);
        if (articleVO == null) {
            articleVO = articleDAO.findByFileId(slug);
        }
        if (articleVO == null) {
            return null;
        }
        String username = userService.userInfoById(articleVO.getUserId()).getShowName();
        articleVO.setUsername(username);
        String filename = articleVO.getName();
        articleVO.setName(filename.substring(0, filename.length() - articleVO.getSuffix().length() - 1));
        setOtherProperties(articleVO);
        return articleVO;
    }

    private void setOtherProperties(MarkdownBaseFile markdownBaseFile) {
        if (markdownBaseFile.getCategoryIds() != null) {
            List<CategoryDO> categories = categoryService.getCategoryListByIds(markdownBaseFile.getCategoryIds());
            markdownBaseFile.setCategories(categories);
        }
        if (markdownBaseFile.getTagIds() != null) {
            List<TagDO> tags = tagService.getTagListByIds(markdownBaseFile.getTagIds());
            markdownBaseFile.setTags(tags);
        }
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
        articleDAO.updatePageSort(list);
        return ResultUtil.success();
    }

    /***
     * 文档列表
     * @return List<MarkdownVO>
     */
    @Override
    public ResponseResult<List<MarkdownVO>> getMarkdownList(ArticleDTO articleDTO) {
        org.springframework.data.domain.Page<FileDocument> page = articleDAO.getMarkdownList(articleDTO);
        List<MarkdownVO> markdownVOList = page.getContent().parallelStream().map(Either.wrap(fileDocument -> getMarkdownVO(fileDocument, BooleanUtil.isTrue(articleDTO.getIsDraft())))).toList();
        return ResultUtil.success(markdownVOList).setCount(page.getTotalElements());
    }

    private MarkdownVO getMarkdownVO(FileDocument fileDocument, boolean isDraft) {
        MarkdownVO markdownVO = new MarkdownVO();
        if (isDraft && fileDocument.getDraft() != null) {
            markdownVO = JacksonUtil.parseObject(fileDocument.getDraft(), MarkdownVO.class);
            markdownVO.setId(fileDocument.getId());
        } else {
            BeanUtils.copyProperties(getFileDocument(fileDocument), markdownVO);
        }
        if (!CharSequenceUtil.isBlank(fileDocument.getDraft())) {
            markdownVO.setDraft(true);
        }
        setOtherProperties(markdownVO);
        String username = userService.userInfoById(fileDocument.getUserId()).getShowName();
        markdownVO.setUsername(username);
        return markdownVO;
    }

    /***
     * 修改fileDocument
     * 去掉文件名后缀
     * 添加用户头像
     * @param fileDocument FileDocument
     */
    private FileDocument getFileDocument(FileDocument fileDocument) {
        ConsumerDO user = userService.userInfoById(fileDocument.getUserId());
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
        if (fileDAO.existsByNameAndIdNotIn(upload.getFilename(), upload.getFileId())) {
            return ResultUtil.warning("该标题已存在");
        }
        boolean isUpdate = false;
        LocalDateTime nowDate = LocalDateTime.now(TimeUntils.ZONE_ID);
        LocalDateTime uploadDate = nowDate;
        if (upload.getUploadDate() != null) {
            uploadDate = upload.getUploadDate();
        }
        FileDocument fileDocument = new FileDocument();
        if (CharSequenceUtil.isNotBlank(upload.getFileId())) {
            isUpdate = true;
            fileDocument = articleDAO.findByFileId(upload.getFileId(), Constants.CONTENT_TEXT, Constants.CONTENT_DRAFT, Constants.CONTENT_HTML);
            if (fileDocument == null) {
                return ResultUtil.warning("该文档不存在");
            }
            if (CommonFileService.isLock(new FileBaseDTO(fileDocument))) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
        } else {
            fileDocument.setId(new ObjectId().toHexString());
        }
        String filename = upload.getFilename();
        // 同步文档文件
        String currentDirectory = syncDocFile(upload, uploadDate, fileDocument, filename);
        fileDocument.setSuffix(MyFileUtils.extName(filename));
        fileDocument.setUserId(upload.getUserId());
        fileDocument.setUpdateDate(nowDate);
        fileDocument.setPath(currentDirectory);
        fileDocument.setSize((long) upload.getContentText().length());
        fileDocument.setContentType(Constants.CONTENT_TYPE_MARK_DOWN);
        fileDocument.setMd5(CalcMd5.getMd5(filename + upload.getContentText()));
        fileDocument.setUploadDate(uploadDate);
        fileDocument.setName(filename);
        fileDocument.setCover(upload.getCover());
        String slug = getSlug(upload.getSlug(), filename, fileDocument.getId());
        fileDocument.setSlug(slug);
        fileDocument.setCategoryIds(List.of(upload.getCategoryIds()));
        fileDocument.setTagIds(List.of(tagService.getTagIdsByNames(upload.getTagNames())));
        fileDocument.setIsFolder(false);
        if (upload.getIsAlonePage() != null && upload.getIsAlonePage()) {
            fileDocument.setAlonePage(true);
        }
        if (upload.getPageSort() != null) {
            fileDocument.setPageSort(upload.getPageSort());
        }
        articleDAO.upsert(upload, isUpdate, fileDocument);
        luceneService.pushCreateIndexQueue(upload.getFileId());
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
        if (!CharSequenceUtil.isBlank(upload.getCurrentDirectory())) {
            currentDirectory = commonFileService.getUserDirectory(upload.getCurrentDirectory());
        } else {
            Path docPaths = Paths.get(fileProperties.getDocumentDir(), TimeUntils.getFileTimeStrOfMonth(uploadDate));
            // docImagePaths 不存在则新建
            commonUserFileService.upsertFolder(docPaths, upload.getUsername(), upload.getUserId());
            currentDirectory = commonFileService.getUserDirectory(docPaths.toString());
        }
        // 文档为草稿时，文件名使用草稿的文件名
        if (!StrUtil.isBlankIfStr(upload.getIsDraft()) && upload.getIsDraft() && !CharSequenceUtil.isBlank(fileDocument.getName())) {
            filename = fileDocument.getName();
        }
        File file = Paths.get(fileProperties.getRootDir(), upload.getUsername(), currentDirectory, filename).toFile();
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        // 当有文件名或文件路径改变的话则把历史文件删掉
        // 文件名是否改变
        boolean isChangeFileName = !CharSequenceUtil.isBlank(fileDocument.getName()) && !filename.equals(fileDocument.getName());
        // 文件路径是否改变
        boolean isChangePath = !CharSequenceUtil.isBlank(fileDocument.getPath()) && !currentDirectory.equals(fileDocument.getPath());
        if (isChangeFileName || isChangePath) {
            Path oldPath = Paths.get(fileProperties.getRootDir(), upload.getUsername(), fileDocument.getPath(), fileDocument.getName());
            if (Files.exists(oldPath)) {
                PathUtil.del(oldPath);
            }
        }
        return currentDirectory;
    }

    @Override
    public ResponseResult<Object> deleteDraft(String fileId, String username) {
        articleDAO.deleteDraft(fileId, username);
        return ResultUtil.success();
    }

    private String getSlug(String newSlug, String name, String fileId) {
        String baseSlug = CharSequenceUtil.isBlank(newSlug) ? name : newSlug;
        if (!fileDAO.existsBySlugAndIdNot(baseSlug, fileId)) {
            return newSlug;
        }
        for (int i = 0; i < 3; i++) {
            String trySlug = baseSlug + RandomUtil.randomInt(1, 10000);
            if (!fileDAO.existsBySlugAndIdNot(trySlug, fileId)) {
                return trySlug;
            }
        }
        return new ObjectId().toHexString();
    }

    @Override
    public ResponseResult<Object> editTextByPath(UploadApiParamDTO upload) {
        File file = new File(Paths.get(fileProperties.getRootDir(), upload.getUsername(), upload.getRelativePath()).toString());
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        if (CommonFileService.isLock(file, fileProperties.getRootDir(), upload.getUsername())) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }
        String userId = upload.getUserId();
        commonFileService.checkPermissionUserId(userId, upload.getOperationPermissionList(), OperationPermission.PUT);
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        // 文件操作日志
        logService.asyncAddLogFileOperation(upload.getUsername(), upload.getRelativePath(), "修改文件");
        // 修改文件之后保存历史版本
        String operator = userLoginHolder.getUsername();
        Completable.fromAction(() -> {
            Path userRootPath = Paths.get(fileProperties.getRootDir(), upload.getUsername());
            Path relativeNioPath = userRootPath.relativize(file.toPath());
            eventPublisher.publishEvent(new FileVersionEvent(this, upload.getUsername(), relativeNioPath.toString(), userId, operator));
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();

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
        if (CharSequenceUtil.isBlank(upload.getUrl())) {
            return ResultUtil.warning("url不能为空");
        }
        Map<String, String> map = new HashMap<>(2);
        String username = upload.getUsername();
        String userId = upload.getUserId();
        Path docImagePaths = getDocImagePaths(upload);
        // docImagePaths 不存在则新建
        commonUserFileService.upsertFolder(docImagePaths, username, userId);
        File newFile;
        try (HttpResponse response = HttpRequest.get(upload.getUrl()).setFollowRedirects(true).executeAsync();
        InputStream inputStream = response.bodyStream()) {
            if (!response.isOk()) {
                throw new HttpException("Server response error with status code: [{}]", response.getStatus());
            }
            String fileName = getFileName(upload.getUrl(), response);
            if (fileName.endsWith(Constants.POINT_SUFFIX_WEBP)) {
                newFile = Paths.get(fileProperties.getRootDir(), username, docImagePaths.toString(), fileName).toFile();
                // 保存原始webp文件
                FileUtil.writeFromStream(inputStream, newFile);
            } else {
                // 去掉fileName中的后缀
                String fileNameWithoutSuffix = StrUtil.removeSuffix(fileName, "." + FileUtil.getSuffix(fileName));
                newFile = Paths.get(fileProperties.getRootDir(), username, docImagePaths.toString(), fileNameWithoutSuffix + Constants.POINT_SUFFIX_WEBP).toFile();
                ImageMagickProcessor.convertToWebpFile(inputStream, newFile);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new CommonException(2, "上传失败");
        }
        String fileId = commonUserFileService.createFile(username, newFile, userId, true);
        map.put(Constants.FILE_ID, fileId);
        String filepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode(WebConfig.API_FILE_PREFIX + Paths.get(username, docImagePaths.toString(), newFile.getName()), StandardCharsets.UTF_8);
        map.put("url", filepath);
        map.put("originalURL", upload.getUrl());
        map.put(Constants.FILENAME, newFile.getName());
        map.put(Constants.FILE_PATH, filepath);
        return ResultUtil.success(map);
    }

    /**
     * 获取url中的文件名
     * @param imageUrl 图片链接
     * @param response HttpResponse
     * @return 文件名
     */
    private static String getFileName(String imageUrl, HttpResponse response) {
        // 从头信息中获取文件名
        String fileName = response.getFileNameFromDisposition(null);
        if (StrUtil.isBlank(fileName)) {
            final String path = URLUtil.getPath(imageUrl);
            // 从路径中获取文件名
            fileName = StrUtil.subSuf(path, path.lastIndexOf('/') + 1);
            if (StrUtil.isBlank(fileName)) {
                // 编码后的路径做为文件名
                fileName = URLUtil.encodeQuery(path, StandardCharsets.UTF_8);
            } else {
                // issue#I4K0FS@Gitee
                fileName = URLUtil.decode(fileName, StandardCharsets.UTF_8);
            }
        }
        return fileName;
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

        Path docImagePaths = getDocImagePaths(upload);

        String username = upload.getUsername();
        String userId = upload.getUserId();
        // docImagePaths 不存在则新建
        commonUserFileService.upsertFolder(docImagePaths, username, userId);
        File newFile;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            if (commonUserFileService.getDisabledWebp(userId) || ("ico".equals(FileUtil.getSuffix(fileName)))) {
                newFile = Paths.get(fileProperties.getRootDir(), username, docImagePaths.toString(), fileName).toFile();
                FileUtil.writeFromStream(inputStream, newFile);
            } else {
                if (!fileName.endsWith(Constants.POINT_SUFFIX_WEBP)) {
                    fileName = fileName + Constants.POINT_SUFFIX_WEBP;
                }
                newFile = Paths.get(fileProperties.getRootDir(), username, docImagePaths.toString(), fileName).toFile();
                ImageMagickProcessor.convertToWebpFile(inputStream, newFile);
            }
        } catch (IOException e) {
            throw new CommonException(2, "上传失败");
        }
        String fileId = commonUserFileService.createFile(username, newFile, userId, true);
        map.put(Constants.FILE_ID, fileId);
        String filepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode(WebConfig.API_FILE_PREFIX + Paths.get(username, docImagePaths.toString(), fileName), StandardCharsets.UTF_8);
        map.put(Constants.FILENAME, fileName);
        map.put(Constants.FILE_PATH, filepath);
        return map;
    }

    @NotNull
    private Path getDocImagePaths(UploadImageDTO upload) {
        Path docImagePaths;
        String markdownFileId = upload.getFileId();
        if (CharSequenceUtil.isNotBlank(markdownFileId) && !"undefined".equals(markdownFileId)) {
            FileDocument fileDocument = commonFileService.getById(markdownFileId);
            if (fileDocument == null) {
                throw new CommonException(ExceptionType.FILE_NOT_FIND);
            }
            docImagePaths = Paths.get(fileDocument.getPath());
        } else {
            docImagePaths = Paths.get(fileProperties.getDocumentImgDir(), TimeUntils.getFileTimeStrOfMonth());
        }
        return docImagePaths;
    }

}
