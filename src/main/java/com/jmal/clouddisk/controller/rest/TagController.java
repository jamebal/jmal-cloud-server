package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.TagDTO;
import com.jmal.clouddisk.service.impl.TagService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description 标签管理
 * @Date 2020/12/02 6:47 下午
 */
@RestController
@Tag(name = "标签管理")
public class TagController {

    @Autowired
    private TagService tagService;

    @Operation(summary = "标签列表")
    @GetMapping("/tag/list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<TagDTO>> list(String userId) {
        return ResultUtil.success(tagService.list(userId));
    }

    @Operation(summary = "标签信息")
    @GetMapping("/tag/info")
    @Permission("website:set:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<TagDTO> tagInfo(@RequestParam String tagId) {
        TagDTO tagDTO = new TagDTO();
        TagDO tag = tagService.getTagInfo(tagId);
        if(tag != null){
            BeanUtils.copyProperties(tag, tagDTO);
        }
        return ResultUtil.success(tagDTO);
    }

    @Operation(summary = "添加标签")
    @PostMapping("/tag/add")
    @Permission("website:set:add")
    @LogOperatingFun
    public ResponseResult<Object> add(@RequestParam String name, @ModelAttribute @Validated TagDTO tagDTO) {
        tagDTO.setName(name);
        return tagService.add(tagDTO);
    }

    @Operation(summary = "更新标签")
    @PutMapping("/tag/update")
    @Permission("website:set:update")
    @LogOperatingFun
    public ResponseResult<Object> update(@RequestParam String name, @ModelAttribute @Validated TagDTO tagDTO) {
        tagDTO.setName(name);
        return tagService.update(tagDTO);
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tag/delete")
    @Permission("website:set:delete")
    @LogOperatingFun
    public ResponseResult<Object> delete(@RequestParam String[] tagIds) {
        List<String> tagIdList = Arrays.asList(tagIds);
        tagService.delete(tagIdList);
        return ResultUtil.success();
    }
}
