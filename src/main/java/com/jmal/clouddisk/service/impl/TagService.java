package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IArticleDAO;
import com.jmal.clouddisk.dao.ITagDAO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.TagDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:51 下午
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final MongoTemplate mongoTemplate;

    private final ITagDAO tagDAO;

    private final IArticleDAO articleDAO;

    /**
     * 标签列表
     * @param userId userId
     * @return List<TagDTO>
     */
    public List<TagDTO> list(String userId) {
        List<TagDO> tagList = tagDAO.findTags(userId);
        return tagList.stream()
                .map(tag -> {
                    TagDTO tagDTO = new TagDTO();
                    BeanUtils.copyProperties(tag, tagDTO);
                    return tagDTO;
                })
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 带有文章数的标签列表
     * @return List<TagDTO>
     */
    public List<TagDTO> listTagsOfArticle() {
        List<TagDO> tagList = tagDAO.findTags(null);
        return tagList.stream().map(tag -> {
            TagDTO tagDTO = new TagDTO();
            BeanUtils.copyProperties(tag, tagDTO);
            getTagArticlesNum(tagDTO);
            tagDTO.setFontSize(Math.round(Math.log(tagDTO.getArticleNum() * 5d) * 10d));
            tagDTO.setColor("rgb(" + Math.round(Math.random() * 100 + 80) + "," + Math.round(Math.random() * 100 + 80) + "," + Math.round(Math.random() * 100 + 80) + ")");
            return tagDTO;
        }).filter(tag -> tag.getArticleNum() > 0).sorted().toList();
    }

    /**
     * 获取标签的文章数
     * @param tagDTO tagDTO
     */
    private void getTagArticlesNum(TagDTO tagDTO) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.TAG_IDS).is(tagDTO.getId()));
        query.addCriteria(Criteria.where(Constants.RELEASE).is(true));
        tagDTO.setArticleNum(mongoTemplate.count(query, CommonFileService.COLLECTION_NAME));
    }

    /***
     * 获取标签信息
     * @param userId 用户id
     * @param tagName 标签名
     * @return 一个分类信息
     */
    public TagDO getTagInfo(String userId, String tagName) {
        return tagDAO.findOneTagByUserIdAndName(userId, tagName);
    }

    /***
     * 获取标签信息
     * @param userId 用户id
     * @param tagSlugName 标签缩略名
     * @return 一个分类信息
     */
    public TagDO getTagInfoBySlug(String userId, String tagSlugName) {
        TagDO tag = tagDAO.findOneTagByUserIdAndSlugName(userId, tagSlugName);
        if (tag == null) {
            tag = getTagInfo(userId, tagSlugName);
        }
        return tag;
    }

    /***
     * 标签信息
     * @param tagId tagId
     * @return Tag
     */
    public TagDO getTagInfo(String tagId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(tagId));
        return mongoTemplate.findOne(query, TagDO.class);
    }

    /***
     * 添加标签
     * @param tagDTO TagDTO
     * @return ResponseResult
     */
    public ResponseResult<Object> add(TagDTO tagDTO) {
        if (tagExists(tagDTO)) {
            return ResultUtil.warning("该标签名称已存在");
        }
        tagDTO.setSlug(getSlug(tagDTO));
        TagDO tag = new TagDO();
        BeanUtils.copyProperties(tagDTO, tag);
        tag.setId(null);
        mongoTemplate.save(tag);
        return ResultUtil.success();
    }

    private boolean tagExists(TagDTO tagDTO) {
        TagDO tag = getTagInfo(tagDTO.getUserId(), tagDTO.getName());
        return tag != null;
    }

    /***
     * 更新标签
     * @param tagDTO TagDTO
     * @return ResponseResult
     */
    public ResponseResult<Object> update(TagDTO tagDTO) {
        TagDO tag1 = getTagInfo(tagDTO.getId());
        if (tag1 == null) {
            return ResultUtil.warning("该标签不存在");
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").nin(tagDTO.getId()));
        query1.addCriteria(Criteria.where("name").is(tagDTO.getName()));
        if (mongoTemplate.exists(query1, TagDO.class)) {
            return ResultUtil.warning("该标签名称已存在");
        }
        tagDTO.setSlug(getSlug(tagDTO));
        TagDO tag = new TagDO();
        BeanUtils.copyProperties(tagDTO, tag);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(tagDTO.getId()));
        Update update = MongoUtil.getUpdate(tag);
        mongoTemplate.upsert(query, update, TagDO.class);
        return ResultUtil.success();
    }

    private String getSlug(TagDTO tagDTO) {
        Query query = new Query();
        String slug = tagDTO.getSlug();
        if (CharSequenceUtil.isBlank(slug)) {
            return tagDTO.getName();
        }
        String id = tagDTO.getId();
        if (id != null) {
            query.addCriteria(Criteria.where("_id").nin(id));
        }
        query.addCriteria(Criteria.where("slug").is(slug));
        if (mongoTemplate.exists(query, TagDO.class)) {
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
        mongoTemplate.remove(query, TagDO.class);
    }

    /**
     * 根据标签名获取标签Id
     * 没有相应的标签名则添加
     *
     * @param tagNames 签名集合
     * @return 标签Id集合
     */
    public String[] getTagIdsByNames(String[] tagNames) {
        if (tagNames == null) {
            return new String[]{};
        }
        List<String> tagNameList = Arrays.asList(tagNames);
        return tagNameList.parallelStream().map(tagName -> getTagIdByName(tagName, null, null).getTagId()).toArray(String[]::new);
    }

    /**
     * 根据标签DTO列表获取标签Id
     *
     * @param tagDTOList 标签DTO列表
     * @param userId     用户id
     * @return 标签Id集合
     */
    public List<Tag> getTagIdsByTagDTOList(List<TagDTO> tagDTOList, String userId) {
        if (tagDTOList == null) {
            return new ArrayList<>();
        }
        return tagDTOList.parallelStream().map(tagDTO -> getTagIdByName(tagDTO.getName(), tagDTO.getColor(), userId)).collect(Collectors.toList());
    }

    private Tag getTagIdByName(String tagName, String color, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(tagName));
        TagDO tag = mongoTemplate.findOne(query, TagDO.class);
        if (tag == null) {
            // 添加新标签
            tag = new TagDO();
            tag.setId(new ObjectId().toHexString());
        } else {
            boolean tagChange = CharSequenceUtil.isNotBlank(color) && !color.equals(tag.getColor());
            if (CharSequenceUtil.isNotBlank(tagName) && !tagName.equals(tag.getName())) {
                tagChange = true;
            }
            if (tagChange) {
                // 更改了标签, 则需修改fileDocument中tags
                updateFilDocumentTag(color, tagName, tag);
            }
        }
        if (CharSequenceUtil.isNotBlank(userId)) {
            tag.setUserId(userId);
        }
        if (CharSequenceUtil.isNotBlank(color)) {
            tag.setColor(color);
        }
        tag.setName(tagName);
        tag.setSlug(tagName);
        mongoTemplate.save(tag);
        Tag fileDocumentTag = new Tag();
        fileDocumentTag.setTagId(tag.getId());
        fileDocumentTag.setName(tagName);
        fileDocumentTag.setColor(tag.getColor());
        return fileDocumentTag;
    }

    /**
     * 更新fileDocument中tags
     * @param color 新的颜色
     * @param tagName 新的标签名
     * @param tag 标签
     */
    private void updateFilDocumentTag(String color, String tagName, TagDO tag) {
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("tags.tagId").is(tag.getId()));
        Update update = new Update();
        update.set("tags.$.color", color);
        update.set("tags.$.name", tagName);
        mongoTemplate.updateMulti(query1,update, CommonFileService.COLLECTION_NAME);
    }

    /***
     * 根据id查询标签列表
     * @param tagIds 标签id集合
     * @return 标签列表
     */
    public List<TagDO> getTagListByIds(Object[] tagIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(tagIds));
        return mongoTemplate.find(query, TagDO.class);
    }

    /**
     * 更新标签排序
     * @param tagIdList 标签Id列表
     */
    public void updateTagSort(List<String> tagIdList) {
        if (tagIdList == null || tagIdList.isEmpty()) {
            return;
        }
        List<TagDTO> tagDTOList = new ArrayList<>();
        for (int i = 0; i < tagIdList.size(); i++) {
            TagDTO tagDTO = new TagDTO();
            tagDTO.setSort(i);
            tagDTO.setId(tagIdList.get(i));
            tagDTOList.add(tagDTO);
        }
        tagDTOList.parallelStream().forEach(tagDTO -> {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(tagDTO.getId()));
            Update update = new Update();
            update.set("sort", tagDTO.getSort());
            mongoTemplate.updateFirst(query, update, TagDO.class);
        });
    }
}
