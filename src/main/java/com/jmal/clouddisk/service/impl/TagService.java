package com.jmal.clouddisk.service.impl;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.TagDTO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:51 下午
 */
@Service
public class TagService {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String USERID_PARAM = "userId";

    private static final String COLLECTION_NAME = "tag";

    /***
     * 标签列表
     * @param userId userId
     * @return List<TagDTO>
     */
    public List<TagDTO> list(String userId) {
        Query query = getQueryUserId(userId);
        List<Tag> tagList = mongoTemplate.find(query,Tag.class, COLLECTION_NAME);
        return tagList.parallelStream().map(tag -> {
            TagDTO tagDTO = new TagDTO();
            CglibUtil.copy(tag, tagDTO);
            return tagDTO;
        }).sorted().collect(Collectors.toList());
    }

    /***
     * 带有文章数的标签列表
     * @return List<TagDTO>
     */
    public List<TagDTO> listTagsOfArticle() {
        Query query = getQueryUserId(null);
        List<Tag> tagList = mongoTemplate.find(query, Tag.class, COLLECTION_NAME);
        return tagList.parallelStream().map(tag -> {
            TagDTO tagDTO = new TagDTO();
            CglibUtil.copy(tag, tagDTO);
            getTagArticlesNum(tagDTO);
            tagDTO.setFontSize(Math.round(Math.log(tagDTO.getArticleNum()  * 5) *  10));
            tagDTO.setColor("rgb("+Math.round(Math.random() * 100 + 80)+","+Math.round(Math.random() * 100 + 80)+","+Math.round(Math.random() * 100 + 80)+")");
            return tagDTO;
        }).filter(tag -> tag.getArticleNum() > 0).sorted().collect(Collectors.toList());
    }

    /***
     * 获取标签的文章数
     * @param tagDTO tagDTO
     */
    private void getTagArticlesNum(TagDTO tagDTO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("tagIds").is(tagDTO.getId()));
        query.addCriteria(Criteria.where("release").is(true));
        tagDTO.setArticleNum(mongoTemplate.count(query, FileServiceImpl.COLLECTION_NAME));
    }

    /***
     * 获取查询条件
     * @param userId userId
     * @return Query
     */
    private Query getQueryUserId(String userId) {
        Query query = new Query();
        if (!StringUtils.isEmpty(userId)) {
            query.addCriteria(Criteria.where(USERID_PARAM).is(userId));
        } else {
            query.addCriteria(Criteria.where(USERID_PARAM).exists(false));
        }
        return query;
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param tagName 标签名
     * @return 一个分类信息
     */
    public Tag getTagInfo(String userId, String tagName) {
        Query query = getQueryUserId(userId);
        query.addCriteria(Criteria.where("name").is(tagName));
        return mongoTemplate.findOne(query, Tag.class, COLLECTION_NAME);
    }

    /***
     * 获取分类信息
     * @param userId 用户id
     * @param tagSlugName 标签缩略名
     * @return 一个分类信息
     */
    public Tag getTagInfoBySlug(String userId, String tagSlugName) {
        Query query = getQueryUserId(userId);
        query.addCriteria(Criteria.where("slug").is(tagSlugName));
        Tag tag = mongoTemplate.findOne(query, Tag.class, COLLECTION_NAME);
        if(tag == null){
            tag = getTagInfo(userId, tagSlugName);
        }
        return tag;
    }

    /***
     * 标签信息
     * @param tagId tagId
     * @return Tag
     */
    public Tag getTagInfo(String tagId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(tagId));
        return mongoTemplate.findOne(query, Tag.class, COLLECTION_NAME);
    }

    /***
     * 添加标签
     * @param tagDTO TagDTO
     * @return ResponseResult
     */
    public ResponseResult<Object> add(TagDTO tagDTO) {
        if (tagExists(tagDTO)) {
            return ResultUtil.warning("该标签名称以存在");
        }
        tagDTO.setSlug(getSlug(tagDTO));
        Tag tag = new Tag();
        CglibUtil.copy(tagDTO, tag);
        mongoTemplate.save(tag, COLLECTION_NAME);
        return ResultUtil.success();
    }

    private boolean tagExists(TagDTO tagDTO) {
        Tag tag = getTagInfo(tagDTO.getUserId(), tagDTO.getName());
        return tag != null;
    }

    /***
     * 更新标签
     * @param tagDTO TagDTO
     * @return ResponseResult
     */
    public ResponseResult<Object> update(TagDTO tagDTO) {
        Tag tag1 = getTagInfo(tagDTO.getId());
        if (tag1 == null) {
            return ResultUtil.warning("该标签不存在");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(tagDTO.getId()));
        query1.addCriteria(Criteria.where("name").is(tagDTO.getName()));
        if(mongoTemplate.exists(query1, COLLECTION_NAME)){
            return ResultUtil.warning("该标签名称已存在");
        }
        tagDTO.setSlug(getSlug(tagDTO));
        Tag tag = new Tag();
        CglibUtil.copy(tagDTO, tag);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(tagDTO.getId()));
        Update update = MongoUtil.getUpdate(tag);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    private String getSlug(TagDTO tagDTO) {
        Query query = new Query();
        String slug = tagDTO.getSlug();
        if (StringUtils.isEmpty(slug)) {
            return tagDTO.getName();
        }
        String id = tagDTO.getId();
        if (id != null) {
            query.addCriteria(Criteria.where("_id").nin(id));
        }
        query.addCriteria(Criteria.where("slug").is(slug));
        if (mongoTemplate.exists(query, COLLECTION_NAME)) {
            return slug + "-1";
        }
        return slug;
    }

    /***
     * 删除标签
     * @param tagIdList tagIdList
     */
    public void delete(List<String> tagIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(tagIdList));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    /**
     * 根据标签名获取标签Id
     * 没有相应的标签名则添加
     * @param tagNames 签名集合
     * @return 标签Id集合
     */
    public String[] getTagIdsByNames(String[] tagNames) {
        if(tagNames == null){
            return new String[]{};
        }
        List<String> tagNameList = Arrays.asList(tagNames);
        return tagNameList.parallelStream().map(this::getTagIdByName).toArray(String[]::new);
    }

    private String getTagIdByName(String tagName){
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(tagName));
        Tag tag = mongoTemplate.findOne(query, Tag.class, COLLECTION_NAME);
        if(tag == null){
            tag = new Tag();
            tag.setId(new ObjectId().toHexString());
            tag.setName(tagName);
            tag.setSlug(tagName);
            mongoTemplate.save(tag, COLLECTION_NAME);
        }
        return tag.getId();
    }

    /***
     * 根据id查询标签列表
     * @param tagIds 标签id集合
     * @return 标签列表
     */
    public List<Tag> getTagListByIds(String[] tagIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(tagIds));
        return mongoTemplate.find(query, Tag.class, COLLECTION_NAME);
    }
}
